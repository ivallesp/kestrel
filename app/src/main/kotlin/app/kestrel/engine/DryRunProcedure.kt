package app.kestrel.engine

import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import app.kestrel.model.StatusFamily
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * One dry run (see [DryRun]): load the item, open eBay's bid layer, type the max, read the minimum and close the layer.
 * It works on a [BidLayerPreview], which has no submit step, so it cannot place a bid even by mistake.
 */
internal class DryRunProcedure(
    private val store: SnipeStore,
    notifier: SnipeNotifier,
    private val clock: EngineClock,
    minimumBidRule: MinimumBidRule,
) {
    private val journal = EngineJournal(store, notifier, clock)
    private val writer = SnipeWriter(store, clock)
    private val prices = PriceGuard(minimumBidRule)
    private val groups = GroupGate(store, writer, journal)

    suspend fun run(
        snipeId: Long,
        preview: BidLayerPreview,
    ): DryRunOutcome {
        val read = callWithin(WindowTimings.STORE_TIMEOUT) { store.snipe(snipeId) }
        if (read !is Call.Done) return DryRunOutcome.Skipped(read.failureText("Reading the snipe"))
        val snipe = read.value ?: return DryRunOutcome.Skipped("The snipe no longer exists.")
        if (snipe.status.isFinal) return DryRunOutcome.Skipped("The snipe has already finished.")
        val windowRunning = snipe.status.family == StatusFamily.BIDDING
        if (windowRunning) return DryRunOutcome.Skipped("The bid window is already running.")
        val max = snipe.maxBid ?: return DryRunOutcome.Skipped("Watching only: there is no bid to test.")
        if (groups.skipIfComplete(snipe) != null) return DryRunOutcome.Skipped("Another snipe in the group was won.")
        val loaded =
            when (val load = load(snipe, preview)) {
                is DryRunLoad.Loaded -> load
                is DryRunLoad.Problem -> return fail(snipe, load.reason, load.text, report = null)
            }
        val listing = loaded.listing
        val otherItem = listing.otherItemThan(snipe.ref, loaded.pageUrl)
        if (otherItem != null) {
            val text =
                "eBay now shows another item number ($otherItem): the seller relisted it. " +
                    "Add the new listing to snipe it."
            return fail(snipe, NeedsUserReason.UNKNOWN_PAGE, text, null)
        }
        if (listing.status == ListingStatus.ENDED) {
            return DryRunOutcome.Skipped("The listing has already ended; the next check records the result.")
        }
        if (!listing.isAuction) {
            return fail(
                snipe,
                NeedsUserReason.UNKNOWN_PAGE,
                "This listing is not an auction, so there is nothing to bid on.",
                null,
            )
        }
        return tryTheLayer(snipe, listing, max, preview)
    }

    private suspend fun tryTheLayer(
        snipe: Snipe,
        listing: Listing,
        max: Money,
        preview: BidLayerPreview,
    ): DryRunOutcome {
        var layerTouched = false
        try {
            val layerCall = callWithin(WindowTimings.OPEN_LAYER_TIMEOUT) { preview.openBidLayer() }
            layerTouched = true
            val layer =
                when (val opened = layerCall.valueOrNull()) {
                    is LayerState.Ready -> {
                        opened
                    }

                    null -> {
                        // The page did not answer in time; its report still says what it showed.
                        val report = callWithin(WindowTimings.REPORT_TIMEOUT) { preview.report() }.valueOrNull()
                        return fail(
                            snipe,
                            NeedsUserReason.DRY_RUN_FAILED,
                            "${layerCall.failureText("Opening eBay's bid layer")}.",
                            report,
                        )
                    }

                    LayerState.NeedsSignIn -> {
                        return fail(
                            snipe,
                            NeedsUserReason.SIGN_IN,
                            "eBay asked you to sign in before bidding. Sign in to eBay in Kestrel again.",
                            null,
                        )
                    }

                    is LayerState.NeedsUser -> {
                        return fail(
                            snipe,
                            NeedsUserReason.VERIFICATION,
                            "eBay needs you before it accepts a bid: ${opened.text}",
                            opened.report,
                        )
                    }

                    is LayerState.Failed -> {
                        return fail(
                            snipe,
                            NeedsUserReason.DRY_RUN_FAILED,
                            "eBay's bid layer did not open: ${opened.reason}",
                            opened.report,
                        )
                    }
                }
            val typedCall = callWithin(WindowTimings.TYPE_TIMEOUT) { preview.typeMax(max) }
            val typed =
                typedCall.valueOrNull()
                    ?: return fail(
                        snipe,
                        NeedsUserReason.DRY_RUN_FAILED,
                        "${typedCall.failureText("Typing your max")}.",
                        layer.report,
                    )
            if (AmountField.minorUnits(typed.fieldValue) != max.minor) {
                return fail(
                    snipe,
                    NeedsUserReason.DRY_RUN_FAILED,
                    "eBay's bid field showed \"${typed.fieldValue}\" instead of your max ${max.displayText()}.",
                    layer.report,
                )
            }
            val minimum = typed.minimum ?: layer.minimum ?: prices.minimumNextBid(listing)
            val overMax =
                prices.isOverMax(minimum, listing.currentPrice, listing.bidCount, max)
                    ?: return fail(
                        snipe,
                        NeedsUserReason.BID_FAILED,
                        "Your max is in ${max.currency}, but this listing takes bids in ${listing.currentPrice.currency}.",
                        layer.report,
                    )
            if (!overMax && typed.inlineError != null) {
                return fail(
                    snipe,
                    NeedsUserReason.DRY_RUN_FAILED,
                    "eBay showed an error for your max: ${typed.inlineError}",
                    layer.report,
                )
            }
            return pass(snipe, listing, max, layer, typed, minimum, overMax)
        } finally {
            if (layerTouched) {
                withContext(NonCancellable) {
                    callWithin(WindowTimings.CLOSE_LAYER_TIMEOUT) { preview.closeLayer() }
                }
            }
        }
    }

    private suspend fun pass(
        snipe: Snipe,
        listing: Listing,
        max: Money,
        layer: LayerState.Ready,
        typed: TypedAmount,
        minimum: Money?,
        overMax: Boolean,
    ): DryRunOutcome {
        val now = clock.nowUtcMillis()
        // The layer opened and took the max, so the session works: earlier sign-in or dry-run problems are over.
        val status = if (overMax) SnipeStatus.PRICE_OVER_MAX else SnipeStatus.ARMED
        val updated =
            writer.updateIfIdle(snipe.id) {
                it.withListing(listing, now).copy(status = status, dryRunAtUtcMillis = now, dryRunPassed = true)
            } ?: return DryRunOutcome.Skipped(TAKEN_OVER)
        journal.event(
            snipe.id,
            TimelineKind.DRY_RUN,
            "Dry run passed: eBay's bid layer opened and took your max ${max.displayText()}. No bid was placed.",
            mapOf(
                "layerReport" to layer.report,
                "submitLabel" to layer.submitLabel,
                "fieldValue" to typed.fieldValue,
                "inlineError" to typed.inlineError,
                "minimum" to minimum,
            ),
        )
        // A sign-in, a check or a failed dry run is over, so the alert that asked for it no longer applies.
        journal.notify { clearNeedsUser(snipe.id) }
        val sameLevel = snipe.lastPrice == listing.currentPrice && snipe.lastBidCount == listing.bidCount
        if (overMax && !(sameLevel && snipe.status == SnipeStatus.PRICE_OVER_MAX)) {
            journal.event(
                snipe.id,
                TimelineKind.PRICE_OVER_MAX,
                "The lowest bid eBay accepts now${minimum?.let {
                    ", ${it.displayText()},"
                } ?: ""} is above your max ${max.displayText()}.",
                mapOf("price" to listing.currentPrice, "minimum" to minimum, "max" to max),
            )
            journal.notify { priceOverMax(updated, listing.currentPrice, minimum) }
        }
        return DryRunOutcome.Passed
    }

    private suspend fun fail(
        snipe: Snipe,
        reason: NeedsUserReason,
        text: String,
        report: String?,
    ): DryRunOutcome {
        val now = clock.nowUtcMillis()
        val updated =
            writer.updateIfIdle(snipe.id) {
                it.copy(status = SnipeStatus.DRY_RUN_FAILED, dryRunAtUtcMillis = now, dryRunPassed = false)
            } ?: return DryRunOutcome.Skipped(TAKEN_OVER)
        journal.event(
            snipe.id,
            TimelineKind.DRY_RUN,
            "Dry run failed: $text No bid was placed.",
            mapOf("reason" to reason.name, "layerReport" to report),
        )
        journal.notify { needsUser(updated, reason, text) }
        return DryRunOutcome.Failed(reason, text)
    }

    // One retry for eBay's own slow-down pages; anything that needs the user stops at once.
    private suspend fun load(
        snipe: Snipe,
        preview: BidLayerPreview,
    ): DryRunLoad {
        val loader = ItemPageLoader(clock, journal, snipe.id)
        var retry: PageLoad.Retry? = null
        for (attempt in 1..LOAD_ATTEMPTS) {
            retry?.let { delay(loader.pauseBeforeRetry(it, attempt)) }
            val readyBy = clock.nowUtcMillis() + WindowTimings.OPEN_ITEM_TIMEOUT + WindowTimings.CHALLENGE_WAIT
            when (val load = loader.loadOnce(preview, snipe.ref, readyBy)) {
                is PageLoad.Ready -> return DryRunLoad.Loaded(load.listing, load.url)
                is PageLoad.HandOff -> return DryRunLoad.Problem(load.handOff.reason, load.handOff.text)
                is PageLoad.Retry -> retry = load
            }
        }
        return DryRunLoad.Problem(
            NeedsUserReason.DRY_RUN_FAILED,
            "${retry?.reason}. Kestrel will still try at the end.",
        )
    }

    private sealed interface DryRunLoad {
        data class Loaded(
            val listing: Listing,
            val pageUrl: String,
        ) : DryRunLoad

        data class Problem(
            val reason: NeedsUserReason,
            val text: String,
        ) : DryRunLoad
    }

    private companion object {
        const val LOAD_ATTEMPTS = 2

        // The window started (or the snipe ended) while the dry run ran; its status is the window's to set.
        const val TAKEN_OVER = "The bid window took over while the dry run ran."
    }
}
