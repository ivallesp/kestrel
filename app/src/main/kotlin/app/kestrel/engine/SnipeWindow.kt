package app.kestrel.engine

import app.kestrel.engine.WindowTimings.ARM_OBSERVER_TIMEOUT
import app.kestrel.engine.WindowTimings.CLOCK_SYNC_TIMEOUT
import app.kestrel.engine.WindowTimings.CLOSE_LAYER_TIMEOUT
import app.kestrel.engine.WindowTimings.CREATE_PAGE_TIMEOUT
import app.kestrel.engine.WindowTimings.DISPATCH_TOLERANCE
import app.kestrel.engine.WindowTimings.HOLD_TICK
import app.kestrel.engine.WindowTimings.LAST_READ_BEFORE_FIRE
import app.kestrel.engine.WindowTimings.LAYER_OPEN_BEFORE_END
import app.kestrel.engine.WindowTimings.LAYER_OPEN_BEFORE_FIRE
import app.kestrel.engine.WindowTimings.LAYER_READY_BEFORE_DISPATCH
import app.kestrel.engine.WindowTimings.MAXIMUM_FOLLOW_AFTER_END
import app.kestrel.engine.WindowTimings.MAXIMUM_HOLD_BEFORE_END
import app.kestrel.engine.WindowTimings.MAXIMUM_REFRESH_FAILURES
import app.kestrel.engine.WindowTimings.MINIMUM_TIME_TO_BID
import app.kestrel.engine.WindowTimings.OPEN_ITEM_TIMEOUT
import app.kestrel.engine.WindowTimings.OPEN_LAYER_TIMEOUT
import app.kestrel.engine.WindowTimings.PAGE_LOAD_BEFORE_END
import app.kestrel.engine.WindowTimings.PAGE_LOAD_BEFORE_FIRE
import app.kestrel.engine.WindowTimings.PAGE_LOAD_RETRIES
import app.kestrel.engine.WindowTimings.PAGE_READY_BEFORE_DISPATCH
import app.kestrel.engine.WindowTimings.POST_END_GRACE
import app.kestrel.engine.WindowTimings.REPORT_TIMEOUT
import app.kestrel.engine.WindowTimings.RESOLVE_ATTEMPTS
import app.kestrel.engine.WindowTimings.RESOLVE_RETRY
import app.kestrel.engine.WindowTimings.RESOLVE_WAIT_MAXIMUM
import app.kestrel.engine.WindowTimings.RESOLVE_WAIT_MINIMUM
import app.kestrel.engine.WindowTimings.STORE_TIMEOUT
import app.kestrel.engine.WindowTimings.TOO_LATE_BEFORE_END
import app.kestrel.engine.WindowTimings.TYPE_MINIMUM_BUDGET
import app.kestrel.engine.WindowTimings.TYPE_TIMEOUT
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * One snipe's final window, from T-120 s to the resolved result (ARCHITECTURE section 5): clock check, item page at
 * T-90 s, bid layer and max at T-45 s, the user's edits lock 3 s before the bid and are read a last time 2 s before
 * it, one press at end - lead (an unreadable reply is followed on the page, never pressed again), then follow the
 * auction through Extended Bidding and read the result. Every wait goes through [clock] and coroutine delay, every
 * call into the page has a time limit, and every way out ends in a stored status, a timeline line and a notification.
 */
internal class SnipeWindow(
    private val snipeId: Long,
    private val store: SnipeStore,
    notifier: SnipeNotifier,
    private val clock: TrueTime,
    private val pages: BidPageFactory,
    minimumBidRule: MinimumBidRule,
) {
    private val journal = EngineJournal(store, notifier, clock)
    private val writer = SnipeWriter(store, clock)
    private val prices = PriceGuard(minimumBidRule)
    private val loader = ItemPageLoader(clock, journal, snipeId)
    private val poller = AuctionPoller(clock, journal, snipeId)
    private val groups = GroupGate(store, writer, journal)
    private val bid = BidRecord()
    private val sender = BidSender(clock, journal, snipeId, bid)

    private lateinit var snipe: Snipe
    private lateinit var maxBid: Money
    private lateinit var watch: AuctionWatch
    private var originalEndUtcMillis = 0L
    private var leadMillis = 0L
    private var startStatus = SnipeStatus.ARMED
    private var page: BidPage? = null
    private var pageReplaced = false
    private var layerOpen = false
    private var typedAmount: Money? = null
    private var endedSignalPending = false
    private var lastAlertedMinimum: Money? = null
    private var instructionReadAtUtcMillis = 0L

    // False after a restart: the bid went out in an earlier run, and the stored max may have been edited since.
    private var bidAmountKnown = true

    private val fireAtUtcMillis: Long get() = watch.endUtcMillis - leadMillis
    private val lockAtUtcMillis: Long get() = fireAtUtcMillis - LAST_READ_BEFORE_FIRE

    // The user's own deadline for edits, a second before the window's last read: the hint below names it.
    private val userEditsLockAtUtcMillis: Long get() = fireAtUtcMillis - BidTiming.EDIT_LOCK_BEFORE_FIRE_MILLIS
    private val pageLoadAtUtcMillis: Long
        get() = minOf(watch.endUtcMillis - PAGE_LOAD_BEFORE_END, fireAtUtcMillis - PAGE_LOAD_BEFORE_FIRE)
    private val layerOpenAtUtcMillis: Long
        get() = minOf(watch.endUtcMillis - LAYER_OPEN_BEFORE_END, fireAtUtcMillis - LAYER_OPEN_BEFORE_FIRE)

    // The bid must leave by then: a second before the end, or just after the fire time for leads that short. Late
    // preparation is bounded by this, not by the fire time: a slightly late bid still beats no bid.
    private val latestDispatchUtcMillis: Long
        get() = maxOf(watch.endUtcMillis - TOO_LATE_BEFORE_END, fireAtUtcMillis + DISPATCH_TOLERANCE)
    private val pageReadyByUtcMillis: Long get() = latestDispatchUtcMillis - PAGE_READY_BEFORE_DISPATCH

    suspend fun run(): SnipeResult {
        val read = callWithin(STORE_TIMEOUT) { store.snipe(snipeId) }
        val start =
            read.valueOrNull()
                ?: return notStarted(
                    SnipeOutcome.FAILED,
                    if (read is Call.Done) "The snipe no longer exists." else read.failureText("Reading the snipe"),
                )
        snipe = start
        if (start.status.isFinal) {
            return start.result
                ?: notStarted(outcomeOfFinishedStatus(start.status), "The snipe had already finished.")
        }
        maxBid =
            start.maxBid
                ?: return notStarted(SnipeOutcome.NOT_PLACED, "Watching only: Kestrel places no bid on this listing.")
        startStatus = start.status
        originalEndUtcMillis = start.endTimeUtcMillis
        leadMillis = BidTiming.leadMillis(start.leadSeconds)
        watch = AuctionWatch(start.ref.site, prices, start.endTimeUtcMillis)
        return try {
            if (start.status in BID_SENT_STATUSES) resumeAfterBid() else prepareAndBid()
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { recordStopped() }
            throw cancellation
        } catch (error: Exception) {
            // Another layer failed in a way nobody foresaw: end with a clear status instead of crashing the service.
            val reason = "Kestrel hit an unexpected error: ${error.message ?: error.javaClass.simpleName}."
            finish(
                if (bid.sent) SnipeOutcome.UNKNOWN else SnipeOutcome.FAILED,
                reason,
                userReason = NeedsUserReason.BID_FAILED,
            )
        } finally {
            withContext(NonCancellable) {
                closePage()
                journal.notify { windowClosed(snipeId) }
            }
        }
    }

    /**
     * The window was stopped from outside (the service went away). Before a bid the snipe goes back to a status a new
     * window can start from, and the user hears about it; after a bid an UNKNOWN result marks the window as finished
     * so the next check reads the result at once.
     */
    private suspend fun recordStopped() {
        if (bid.sent) {
            val reason = "The bid window was stopped after the bid. Kestrel reads the result at the next check."
            val stopped =
                SnipeResult(
                    SnipeOutcome.UNKNOWN,
                    watch.level?.price,
                    bid.sentAtUtcMillis,
                    bid.leadMillis,
                    bid.replyLatencyMillis,
                    bid.replyText,
                    reason,
                )
            snipe = writer.update(snipeId, snipe) { it.copy(status = withBidSent(it.status), result = stopped) }
            journal.event(snipeId, TimelineKind.PROBLEM, reason)
            return
        }
        closeLayerIfUnused()
        val resumeStatus = if (startStatus in RESUMABLE_STATUSES) startStatus else SnipeStatus.ARMED
        snipe = updateBeforeBid { if (it.status == SnipeStatus.BIDDING) it.copy(status = resumeStatus) else it }
        val untilEnd = watch.endUtcMillis - clock.nowUtcMillis()
        val reason =
            if (untilEnd > 0) {
                "The bid window was stopped ${durationText(untilEnd)} before the end, before the bid. " +
                    "Bid on eBay yourself if Kestrel does not get another chance."
            } else {
                "The bid window was stopped before the bid."
            }
        journal.event(snipeId, TimelineKind.PROBLEM, reason)
        // A stop the user asked for (a cancel, now stored as final) needs no alert.
        val stoppedByUser = snipe.status.isFinal
        if (untilEnd > 0 && !stoppedByUser) journal.notify { needsUser(snipe, NeedsUserReason.BID_FAILED, reason) }
    }

    private suspend fun prepareAndBid(): SnipeResult {
        checkGroup(atFire = false)?.let { return it }
        checkClock(endWhenUntrusted = true)?.let { return it }
        startedTooEarly()?.let { return it }
        openWindow()
        startPage()?.let { return it }
        holdUntil({ pageLoadAtUtcMillis }, following = false)?.let { return it }
        loadListing()?.let { return it }
        holdUntil({ layerOpenAtUtcMillis }, following = true)?.let { return it }
        prepareLayer()?.let { return it }
        holdUntil({ lockAtUtcMillis }, following = true)?.let { return it }
        if (!layerOpen) prepareLayer()?.let { return it }
        lock()?.let { return it }
        fire()?.let { return it }
        followToEnd()
        return resolve()
    }

    // A bid went out in an earlier run of this window (the process was restarted): never bid again, only follow.
    private suspend fun resumeAfterBid(): SnipeResult {
        bid.sent = true
        bidAmountKnown = false
        bid.standing =
            when (snipe.status) {
                SnipeStatus.LEADING -> BidStanding.LEADING
                SnipeStatus.OUTBID -> BidStanding.OUTBID
                else -> BidStanding.UNKNOWN
            }
        snipe = writer.update(snipeId, snipe) { it.copy(result = null) }
        journal.event(
            snipeId,
            TimelineKind.WINDOW_OPENED,
            "The bid window reopened after a restart. The bid was already sent, so Kestrel only follows the auction.",
        )
        checkClock(endWhenUntrusted = false)
        val created = callWithin(CREATE_PAGE_TIMEOUT) { pages.create() }
        val currentPage =
            created.valueOrNull()
                ?: return finish(
                    SnipeOutcome.UNKNOWN,
                    "${created.failureText("Opening Kestrel's browser page")}. Check the result on eBay.",
                )
        page = currentPage
        val loaded = loader.loadOnce(currentPage, snipe.ref, clock.nowUtcMillis() + OPEN_ITEM_TIMEOUT)
        if (loaded is PageLoad.Ready && loaded.listing.status == ListingStatus.ACTIVE) {
            watch.startFrom(loaded.listing)
            followToEnd()
        }
        return resolve()
    }

    private suspend fun openWindow() {
        // A window starts without a result; a result written later marks the window as finished for checks.
        snipe = updateBeforeBid { it.copy(status = SnipeStatus.BIDDING, result = null) }
        journal.event(
            snipeId,
            TimelineKind.WINDOW_OPENED,
            "Bid window opened. Kestrel bids your max ${maxBid.displayText()} " +
                "${leadMillis / 1_000} s before the end, in ${durationText(fireAtUtcMillis - clock.nowUtcMillis())}.",
            mapOf("fireAtUtcMillis" to fireAtUtcMillis, "endUtcMillis" to watch.endUtcMillis, "max" to maxBid),
        )
        journal.notify { windowOpened(snipe, fireAtUtcMillis) }
        progress(WindowPhase.CHECKING)
    }

    private suspend fun checkClock(endWhenUntrusted: Boolean): SnipeResult? {
        val call = callWithin(CLOCK_SYNC_TIMEOUT) { clock.sync() }
        if (call is Call.Done && call.value == null && endWhenUntrusted) {
            return finish(
                SnipeOutcome.FAILED,
                "Kestrel could not find a trustworthy time (time.google.com did not answer, Android has no network " +
                    "time and the phone's clock looks wrong), so it did not bid.",
                userReason = NeedsUserReason.BID_FAILED,
            )
        }
        val sync = call.valueOrNull()
        if (sync == null) {
            journal.event(
                snipeId,
                TimelineKind.PROBLEM,
                "${call.failureText("Checking the clock")}. Kestrel uses its last known time.",
            )
            return null
        }
        val message =
            when (sync.source) {
                ClockSources.SNTP -> {
                    "Clock checked against time.google.com: ${offsetText(sync.offsetMillis)} " +
                        "(round trip ${sync.roundTripMillis} ms)."
                }

                ClockSources.SNTP_EARLIER -> {
                    "time.google.com did not answer, so Kestrel uses its recent measurement: " +
                        "${offsetText(sync.offsetMillis)}."
                }

                ClockSources.NETWORK -> {
                    "time.google.com did not answer, so Kestrel uses Android's network time, which can be a second off."
                }

                else -> {
                    "time.google.com did not answer and Android has no network time, so Kestrel uses the phone's own " +
                        "clock, which can be a few seconds off."
                }
            }
        journal.event(
            snipeId,
            TimelineKind.CLOCK_SYNCED,
            message,
            mapOf(
                "source" to sync.source,
                "offsetMillis" to sync.offsetMillis,
                "roundTripMillis" to sync.roundTripMillis,
                "atElapsedRealtimeMillis" to sync.atElapsedRealtimeMillis,
            ),
        )
        return null
    }

    // A window started long before its end (a scheduling slip) would hold the phone awake for nothing.
    private suspend fun startedTooEarly(): SnipeResult? {
        val untilEnd = watch.endUtcMillis - clock.nowUtcMillis()
        if (untilEnd <= MAXIMUM_HOLD_BEFORE_END) return null
        val reason =
            "The bid window started ${durationText(untilEnd)} before the end, which is too early. " +
                "Kestrel opens it again nearer the end."
        journal.event(snipeId, TimelineKind.PROBLEM, reason)
        return notStarted(SnipeOutcome.NOT_PLACED, reason)
    }

    private suspend fun startPage(): SnipeResult? {
        val call = callWithin(CREATE_PAGE_TIMEOUT) { pages.create() }
        page = call.valueOrNull()
        if (page != null) return null
        return finish(
            SnipeOutcome.FAILED,
            "${call.failureText("Opening Kestrel's browser page")}, so Kestrel could not bid.",
            userReason = NeedsUserReason.BID_FAILED,
        )
    }

    /** Waits until [target], re-reading the user's instruction every second and, once the page is up, following the auction. */
    private suspend fun holdUntil(
        target: () -> Long,
        following: Boolean,
    ): SnipeResult? {
        while (true) {
            if (clock.nowUtcMillis() >= target()) return null
            checkUserChanges()?.let { return it }
            if (following) {
                if (replaceDeadPage()) startOverOnNewPage()?.let { return it }
                followBeforeBid(target())?.let { return it }
                rescheduleIfEndMovedFar()?.let { return it }
            }
            val wait = minOf(HOLD_TICK, target() - clock.nowUtcMillis())
            if (wait > 0) delay(wait)
        }
    }

    // The user may raise or lower the max, cancel, or switch to watching during the window, until the edits lock.
    private suspend fun checkUserChanges(): SnipeResult? {
        val read = callWithin(STORE_TIMEOUT) { store.snipe(snipeId) }
        if (read !is Call.Done) return null
        instructionReadAtUtcMillis = clock.nowUtcMillis()
        val fresh = read.value ?: return finishDeleted()
        when {
            fresh.status == SnipeStatus.CANCELLED -> {
                return finish(SnipeOutcome.CANCELLED, "You cancelled the snipe before the bid.")
            }

            fresh.status.isFinal -> {
                // Something else finished the snipe (its group was won, or a check found the listing ended). That
                // already stored and announced the result, so the window only stops.
                closeLayerIfUnused()
                return fresh.result
                    ?: notStarted(outcomeOfFinishedStatus(fresh.status), "The snipe finished before the bid.")
            }

            fresh.maxBid == null -> {
                return finish(
                    SnipeOutcome.CANCELLED,
                    "You switched the snipe to watching only before the bid.",
                    status = SnipeStatus.WATCHING,
                )
            }
        }
        val ownStatus = snipe.status
        snipe = fresh.copy(status = ownStatus)
        // The window owns the status while it runs; a screen saving an older copy must not hide that a bid is under way.
        if (fresh.status != ownStatus) snipe = updateBeforeBid { it.copy(status = ownStatus) }
        val freshLead = BidTiming.leadMillis(fresh.leadSeconds)
        if (freshLead != leadMillis) applyNewLead(freshLead)
        if (fresh.maxBid != maxBid) return applyNewMax(fresh.maxBid)
        return null
    }

    // A new lead moves the fire time (and the lock), as long as the edit arrives before the lock.
    private suspend fun applyNewLead(newLeadMillis: Long) {
        journal.event(
            snipeId,
            TimelineKind.EDITED,
            "You changed when Kestrel bids: ${newLeadMillis / 1_000} s before the end instead of ${leadMillis / 1_000} s.",
            mapOf("previousLeadMillis" to leadMillis, "leadMillis" to newLeadMillis),
        )
        leadMillis = newLeadMillis
        journal.notify { windowOpened(snipe, fireAtUtcMillis) }
    }

    /**
     * Writes before the bid keep a cancel (or a switch to watching) the user saved in the meantime, so the next read of
     * the instruction still sees it.
     */
    private suspend fun updateBeforeBid(change: (Snipe) -> Snipe): Snipe =
        writer.update(snipeId, snipe) { current ->
            val changed = change(current)
            if (current.status.isFinal || current.maxBid == null) changed.copy(status = current.status) else changed
        }

    private suspend fun applyNewMax(newMax: Money): SnipeResult? {
        journal.event(
            snipeId,
            TimelineKind.EDITED,
            "You changed the max during the window: ${maxBid.displayText()} to ${newMax.displayText()}.",
            mapOf("previousMax" to maxBid, "max" to newMax),
        )
        val listingCurrency = watch.level?.price?.currency
        if (listingCurrency != null && newMax.currency != listingCurrency) {
            return finish(
                SnipeOutcome.FAILED,
                "Your new max is in ${newMax.currency}, but this listing takes bids in $listingCurrency, so Kestrel did not bid.",
                userReason = NeedsUserReason.BID_FAILED,
            )
        }
        maxBid = newMax
        if (layerOpen) typeAndVerify(newMax)?.let { return it }
        // The Live Update still shows the old max until the next phase otherwise, which can be the fire itself.
        if (layerOpen) {
            progress(
                WindowPhase.READY,
                "Max ${newMax.displayText()} typed",
            )
        } else {
            progress(WindowPhase.CHECKING)
        }
        alertIfOverMax()
        return null
    }

    private suspend fun loadListing(): SnipeResult? {
        var retries = 0
        while (true) {
            val currentPage =
                page ?: return finish(SnipeOutcome.FAILED, "Kestrel's browser page is gone, so it could not bid.")
            when (val load = loader.loadOnce(currentPage, snipe.ref, pageReadyByUtcMillis)) {
                is PageLoad.Ready -> {
                    return onListingLoaded(load.listing, load.url)
                }

                is PageLoad.HandOff -> {
                    return finish(SnipeOutcome.FAILED, load.handOff.text, userReason = load.handOff.reason)
                }

                is PageLoad.Retry -> {
                    // A dead page fails every load: a fresh one, once, rather than retrying on the dead one.
                    if (replaceDeadPage()) continue
                    retries++
                    if (retries > PAGE_LOAD_RETRIES) {
                        return finish(
                            SnipeOutcome.FAILED,
                            "${load.reason}. Kestrel tried ${PAGE_LOAD_RETRIES + 1} times and gave up without bidding.",
                            userReason = NeedsUserReason.BID_FAILED,
                        )
                    }
                    val pause = loader.pauseBeforeRetry(load, retries)
                    val retryAt = clock.nowUtcMillis() + pause
                    if (retryAt >= pageReadyByUtcMillis) {
                        return finish(
                            SnipeOutcome.FAILED,
                            "${load.reason}, and there was no time left to try again. Kestrel did not bid.",
                            userReason = NeedsUserReason.BID_FAILED,
                        )
                    }
                    journal.event(
                        snipeId,
                        TimelineKind.PROBLEM,
                        "${load.reason}. Trying again in ${durationText(pause)}.",
                    )
                    holdUntil({ retryAt }, following = false)?.let { return it }
                }
            }
        }
    }

    private suspend fun onListingLoaded(
        listing: Listing,
        url: String,
    ): SnipeResult? {
        val otherItem = listing.otherItemThan(snipe.ref, url)
        if (otherItem != null) {
            return finish(
                SnipeOutcome.ENDED_EARLY,
                "eBay now shows another item number ($otherItem): the seller relisted it, so Kestrel did not bid. " +
                    "Add the new listing to snipe it.",
                finalPrice = null,
            )
        }
        val now = clock.nowUtcMillis()
        snipe = updateBeforeBid { it.withListing(listing, now).copy(status = SnipeStatus.BIDDING) }
        callWithin(STORE_TIMEOUT) { store.recordPrice(snipeId, now, listing.currentPrice, listing.bidCount) }
        journal.event(
            snipeId,
            TimelineKind.PAGE_READY,
            "Item page loaded: ${listing.currentPrice.displayText()} with ${listing.bidCount} bids, ending in " +
                "${durationText(listing.endTimeUtcMillis - now)}.",
            mapOf(
                "url" to url,
                "price" to listing.currentPrice,
                "bidCount" to listing.bidCount,
                "endUtcMillis" to listing.endTimeUtcMillis,
                "signedIn" to listing.signedIn,
                "extendedBidding" to listing.extendedBidding,
            ),
        )
        if (!listing.isAuction) {
            return finish(
                SnipeOutcome.FAILED,
                "This listing is not an auction, so Kestrel did not bid.",
                userReason = NeedsUserReason.BID_FAILED,
            )
        }
        if (listing.status == ListingStatus.ENDED) {
            val resolution = ResultResolver.resolve(listing, maxBid, bid.evidence(), originalEndUtcMillis)
            return finish(
                resolution.outcome,
                resolution.reason,
                finalPrice = listing.currentPrice,
                ebayText = listing.endedText,
            )
        }
        if (listing.currentPrice.currency != maxBid.currency) {
            return finish(
                SnipeOutcome.FAILED,
                "Your max is in ${maxBid.currency}, but this listing takes bids in ${listing.currentPrice.currency}, " +
                    "so Kestrel did not bid.",
                userReason = NeedsUserReason.BID_FAILED,
            )
        }
        watch.startFrom(listing)
        if (watch.endUtcMillis != originalEndUtcMillis) journal.notify { windowOpened(snipe, fireAtUtcMillis) }
        if (listing.signedIn == false) {
            journal.event(
                snipeId,
                TimelineKind.PROBLEM,
                "The item page looks signed out. Opening the bid layer will confirm.",
            )
        }
        val remaining = watch.endUtcMillis - clock.nowUtcMillis()
        if (remaining < MINIMUM_TIME_TO_BID) {
            return finish(
                SnipeOutcome.FAILED,
                "The bid window opened too late: only ${durationText(remaining)} were left, so Kestrel did not bid.",
                userReason = NeedsUserReason.BID_FAILED,
            )
        }
        rescheduleIfEndMovedFar()?.let { return it }
        alertIfOverMax()
        return null
    }

    private suspend fun prepareLayer(): SnipeResult? {
        // Read the instruction again right before typing: the max may have changed, or the snipe been cancelled.
        checkUserChanges()?.let { return it }
        openLayer()?.let { return it }
        typeAndVerify(maxBid)?.let { return it }
        val armed = callWithin(ARM_OBSERVER_TIMEOUT) { page?.armReplyObserver() }
        if (armed !is Call.Done) {
            journal.event(
                snipeId,
                TimelineKind.PROBLEM,
                "${armed.failureText("Watching for eBay's reply")}. Kestrel will read the reply from the bid layer.",
            )
        }
        progress(WindowPhase.READY, "Max ${maxBid.displayText()} typed")
        return null
    }

    private suspend fun openLayer(): SnipeResult? {
        var attempt = 0
        while (attempt < LAYER_ATTEMPTS) {
            attempt++
            val currentPage = page ?: break
            val budget =
                minOf(OPEN_LAYER_TIMEOUT, latestDispatchUtcMillis - LAYER_READY_BEFORE_DISPATCH - clock.nowUtcMillis())
            val call = callWithin(budget) { currentPage.openBidLayer() }
            layerOpen = true
            when (val layer = call.valueOrNull()) {
                is LayerState.Ready -> {
                    watch.recordLayerMinimum(layer.minimum)
                    val minimumNote = layer.minimum?.let { "; eBay's minimum bid is ${it.displayText()}" } ?: ""
                    journal.event(
                        snipeId,
                        TimelineKind.LAYER_READY,
                        "eBay's bid layer is open$minimumNote.",
                        mapOf(
                            "layerReport" to layer.report,
                            "submitLabel" to layer.submitLabel,
                            "minimum" to layer.minimum,
                        ),
                    )
                    return null
                }

                LayerState.NeedsSignIn -> {
                    return finish(
                        SnipeOutcome.FAILED,
                        "eBay asked you to sign in before bidding, so Kestrel did not bid. Sign in to eBay in Kestrel again.",
                        userReason = NeedsUserReason.SIGN_IN,
                    )
                }

                is LayerState.NeedsUser -> {
                    return finish(
                        SnipeOutcome.FAILED,
                        "eBay needs you before it accepts a bid (\"${layer.text}\"), so Kestrel did not bid.",
                        userReason = NeedsUserReason.VERIFICATION,
                        report = layer.report,
                    )
                }

                is LayerState.Failed -> {
                    journal.event(
                        snipeId,
                        TimelineKind.PROBLEM,
                        "eBay's bid layer did not open: ${layer.reason}.",
                        mapOf("layerReport" to layer.report, "attempt" to attempt),
                    )
                }

                null -> {
                    journal.event(
                        snipeId,
                        TimelineKind.PROBLEM,
                        "${call.failureText("Opening eBay's bid layer")}.",
                        mapOf("attempt" to attempt),
                    )
                }
            }
            if (replaceDeadPage()) {
                // The item first, on the new page; then the layer gets its full attempts there.
                loadListing()?.let { return it }
                attempt = 0
                continue
            }
            closeLayerIfUnused()
        }
        return finish(
            SnipeOutcome.FAILED,
            "eBay's bid layer did not open, so Kestrel did not bid.",
            userReason = NeedsUserReason.BID_FAILED,
        )
    }

    /**
     * Types [amount] and has the page verify it. The bid only goes ahead when the page says the field provably holds
     * exactly the max and eBay's bid button takes it. A check in the way goes to the user at once; a max below eBay's
     * minimum is left to the lock, which ends the window as "price passed your max" unless the user raises it.
     */
    private suspend fun typeAndVerify(amount: Money): SnipeResult? {
        typedAmount = null
        var lastProblem: String? = null
        for (attempt in 1..TYPE_ATTEMPTS) {
            val currentPage = page ?: break
            val budget = minOf(TYPE_TIMEOUT, latestDispatchUtcMillis - clock.nowUtcMillis() - TYPE_MINIMUM_BUDGET)
            if (budget < TYPE_MINIMUM_BUDGET) break
            val call = callWithin(budget) { currentPage.typeMax(amount) }
            var typed = call.valueOrNull()
            if (typed == null) {
                journal.event(
                    snipeId,
                    TimelineKind.PROBLEM,
                    "${call.failureText("Typing your max")}.",
                    mapOf("attempt" to attempt),
                )
                continue
            }
            // eBay may still be checking the amount: look again without typing, since typing restarts its check.
            if (typed.problem == TypingProblem.BUTTON_DISABLED) typed = waitForBidButton(currentPage, typed)
            when {
                typed.verified && AmountField.minorUnits(typed.fieldValue) == amount.minor -> {
                    acceptTyped(amount, typed)
                    return null
                }

                typed.problem == TypingProblem.NEEDS_USER -> {
                    return finish(
                        SnipeOutcome.FAILED,
                        "eBay needs you before it accepts a bid (\"${typed.inlineError}\"), so Kestrel did not bid.",
                        userReason = NeedsUserReason.VERIFICATION,
                    )
                }

                typed.problem == TypingProblem.BELOW_MINIMUM -> {
                    watch.recordLayerMinimum(typed.minimum)
                    journal.event(
                        snipeId,
                        TimelineKind.PROBLEM,
                        "eBay did not take your max ${amount.displayText()}: \"${typed.inlineError}\".",
                        mapOf("fieldValue" to typed.fieldValue, "minimum" to typed.minimum),
                    )
                    alertIfOverMax()
                    return null
                }

                else -> {
                    lastProblem = typed.inlineError
                    journal.event(
                        snipeId,
                        TimelineKind.PROBLEM,
                        "eBay's bid field showed \"${typed.fieldValue}\" and the page did not confirm your max " +
                            "${amount.displayText()}: ${typed.inlineError ?: "no reason given"}.",
                        mapOf("attempt" to attempt, "inlineError" to typed.inlineError, "problem" to typed.problem),
                    )
                }
            }
        }
        if (replaceDeadPage()) {
            // The page died: the item and the layer again on a fresh page, then the typing.
            loadListing()?.let { return it }
            openLayer()?.let { return it }
            return typeAndVerify(amount)
        }
        val because = lastProblem?.let { " ($it)" } ?: ""
        return finish(
            SnipeOutcome.FAILED,
            "Kestrel could not type your max into eBay's bid field and confirm it$because, so it did not bid.",
            userReason = NeedsUserReason.BID_FAILED,
        )
    }

    /**
     * When the page died before the bid (its renderer gone: Android's memory limiter, a crash), it never works again,
     * and every WebView of the app may have lost the same renderer. With at least [REBUILD_MINIMUM_MILLIS] left, the
     * window starts once more on a fresh page (android.md WV4). Never after a press: a fresh page could press again.
     * Returns true when a fresh page is in place; the caller loads the item on it again.
     */
    private suspend fun replaceDeadPage(): Boolean {
        val current = page ?: return false
        if (current.isUsable || pageReplaced || bid.submits > 0) return false
        val untilDispatch = latestDispatchUtcMillis - clock.nowUtcMillis()
        if (untilDispatch < REBUILD_MINIMUM_MILLIS) return false
        journal.event(
            snipeId,
            TimelineKind.PROBLEM,
            "Kestrel's browser page stopped ${durationText(watch.endUtcMillis - clock.nowUtcMillis())} before the " +
                "end (Android ended its renderer). Kestrel starts again on a new page.",
        )
        layerOpen = false
        typedAmount = null
        return replacePage() != null
    }

    /** On a fresh page: the item again, and the bid layer again once its time has come. */
    private suspend fun startOverOnNewPage(): SnipeResult? {
        loadListing()?.let { return it }
        return if (clock.nowUtcMillis() >= layerOpenAtUtcMillis) prepareLayer() else null
    }

    private suspend fun acceptTyped(
        amount: Money,
        typed: TypedAmount,
    ) {
        typedAmount = amount
        watch.recordLayerMinimum(typed.minimum)
        journal.event(
            snipeId,
            TimelineKind.LAYER_READY,
            "Typed your max ${amount.displayText()} and eBay's page confirmed it.",
            mapOf("fieldValue" to typed.fieldValue, "inlineError" to typed.inlineError, "minimum" to typed.minimum),
        )
        alertIfOverMax()
    }

    /** Looks at the layer again every second, for a while, until eBay enables its bid button for the typed amount. */
    private suspend fun waitForBidButton(
        currentPage: BidPage,
        typed: TypedAmount,
    ): TypedAmount {
        var latest = typed
        val until = minOf(clock.nowUtcMillis() + BID_BUTTON_WAIT_MILLIS, latestDispatchUtcMillis - TYPE_TIMEOUT)
        while (latest.problem == TypingProblem.BUTTON_DISABLED && clock.nowUtcMillis() + HOLD_TICK <= until) {
            delay(HOLD_TICK)
            latest = callWithin(TYPE_TIMEOUT) { currentPage.checkTyped() }.valueOrNull() ?: return latest
        }
        return latest
    }

    private suspend fun followBeforeBid(deadline: Long): SnipeResult? {
        val currentPage = page ?: return null
        val state = poller.pollIfDue(currentPage, deadline) ?: return null
        val update = watch.apply(state)
        recordUpdate(update)
        if (!update.ended) {
            endedSignalPending = false
            return null
        }
        // One stray "ended" reply must not cost the bid: ask again at once and believe two in a row.
        if (!endedSignalPending) {
            endedSignalPending = true
            poller.pollSoon()
            return null
        }
        return endedBeforeBid()
    }

    private suspend fun recordUpdate(update: AuctionUpdate) {
        update.newLevel?.let { onNewPriceLevel(it) }
        update.extendedFromUtcMillis?.let { onExtended(it) }
        update.movedEarlierFromUtcMillis?.let { previousEnd ->
            journal.event(
                snipeId,
                TimelineKind.PROBLEM,
                "eBay's live update moved the end ${durationText(previousEnd - watch.endUtcMillis)} earlier.",
            )
            // Stored at once: other members of a group order themselves by this end.
            snipe = writer.update(snipeId, snipe) { it.copy(endTimeUtcMillis = watch.endUtcMillis) }
            if (!bid.sent) journal.notify { windowOpened(snipe, fireAtUtcMillis) }
        }
    }

    private suspend fun onNewPriceLevel(level: PriceLevel) {
        val now = clock.nowUtcMillis()
        callWithin(STORE_TIMEOUT) { store.recordPrice(snipeId, now, level.price, level.bidCount) }
        val outbid = bid.sent && level.price.isAbove(maxBid) == true && bid.standing != BidStanding.OUTBID
        if (outbid) bid.standing = BidStanding.OUTBID
        snipe =
            writer.update(snipeId, snipe) {
                it.copy(
                    lastPrice = level.price,
                    lastBidCount = level.bidCount,
                    status = if (outbid) SnipeStatus.OUTBID else it.status,
                )
            }
        if (outbid) {
            journal.event(
                snipeId,
                TimelineKind.EBAY_REPLY,
                "The price ${level.price.displayText()} is above your max ${maxBid.displayText()}: another bidder is ahead.",
            )
            progress(WindowPhase.OUTBID, "Price ${level.price.displayText()}")
        }
        if (!bid.sent) alertIfOverMax()
    }

    private suspend fun onExtended(previousEndUtcMillis: Long) {
        val added = durationText(watch.endUtcMillis - previousEndUtcMillis)
        journal.event(
            snipeId,
            TimelineKind.EXTENDED,
            "eBay extended the auction by $added (extension ${watch.extensions}).",
            mapOf("previousEndUtcMillis" to previousEndUtcMillis, "endUtcMillis" to watch.endUtcMillis),
        )
        snipe =
            writer.update(snipeId, snipe) {
                it.copy(
                    endTimeUtcMillis = watch.endUtcMillis,
                    extendedBidding = true,
                    status = if (bid.sent) SnipeStatus.EXTENDED else it.status,
                )
            }
        progress(WindowPhase.EXTENDED, "Ends $added later")
        // Before the bid the fire time moves with the end, and so does the countdown.
        if (!bid.sent) journal.notify { windowOpened(snipe, fireAtUtcMillis) }
    }

    private suspend fun alertIfOverMax() {
        val minimum = watch.minimumNextBid()
        val over = prices.isOverMax(minimum, watch.level?.price, watch.level?.bidCount, maxBid)
        if (over != true) return
        val level = minimum ?: watch.level?.price ?: return
        if (level == lastAlertedMinimum) return
        lastAlertedMinimum = level
        val current = watch.level?.price ?: level
        val untilLock = userEditsLockAtUtcMillis - clock.nowUtcMillis()
        val raiseHint = if (untilLock > 0) " Raise the max within ${durationText(untilLock)} to still bid." else ""
        journal.event(
            snipeId,
            TimelineKind.PRICE_OVER_MAX,
            if (minimum != null) {
                "The next bid would have to be at least ${minimum.displayText()}, above your max " +
                    "${maxBid.displayText()}.$raiseHint"
            } else {
                "The price ${current.displayText()} has reached your max ${maxBid.displayText()}.$raiseHint"
            },
            mapOf("price" to current, "minimum" to minimum, "max" to maxBid),
        )
        journal.notify { priceOverMax(snipe, current, minimum) }
    }

    private suspend fun endedBeforeBid(): SnipeResult? {
        journal.event(snipeId, TimelineKind.PROBLEM, "eBay's live updates say the auction ended before Kestrel's bid.")
        closeLayerIfUnused()
        // The reload must leave time to prepare the bid again, so it gets no more than the page-ready deadline.
        val budget = minOf(OPEN_ITEM_TIMEOUT, pageReadyByUtcMillis - clock.nowUtcMillis())
        val listing = if (budget > 0) reloadForResult(budget) else null
        if (listing != null && listing.status == ListingStatus.ENDED) {
            val resolution = ResultResolver.resolve(listing, maxBid, bid.evidence(), originalEndUtcMillis)
            return finish(
                resolution.outcome,
                resolution.reason,
                finalPrice = listing.currentPrice,
                ebayText = listing.endedText,
            )
        }
        if (listing?.status == ListingStatus.ACTIVE && listing.endTimeUtcMillis > clock.nowUtcMillis()) {
            journal.event(
                snipeId,
                TimelineKind.PROBLEM,
                "The item page still shows the auction running, so Kestrel carries on.",
            )
            watch.startFrom(listing)
        } else {
            // An ended listing has no bid button, so going on with the bid is harmless; stopping here could cost it.
            journal.event(
                snipeId,
                TimelineKind.PROBLEM,
                "The item page could not confirm that the auction ended, so Kestrel carries on with the bid.",
            )
            watch.disregardEndedSignal()
        }
        endedSignalPending = false
        // The reload left the bid layer. Past the layer's time it is prepared again now, not at the lock.
        layerOpen = false
        typedAmount = null
        return if (clock.nowUtcMillis() >= layerOpenAtUtcMillis) prepareLayer() else null
    }

    // The end moved far into the future (a long extension or a corrected end): release the phone and come back later.
    private suspend fun rescheduleIfEndMovedFar(): SnipeResult? {
        if (bid.sent || watch.endUtcMillis - clock.nowUtcMillis() <= MAXIMUM_HOLD_BEFORE_END) return null
        closeLayerIfUnused()
        val reason =
            "eBay now says the auction ends in ${durationText(watch.endUtcMillis - clock.nowUtcMillis())}. Kestrel " +
                "closed this bid window and opens a new one before the new end."
        val resumeStatus = if (startStatus in RESUMABLE_STATUSES) startStatus else SnipeStatus.ARMED
        snipe = updateBeforeBid { it.copy(status = resumeStatus, endTimeUtcMillis = watch.endUtcMillis) }
        journal.event(snipeId, TimelineKind.PROBLEM, reason, mapOf("endUtcMillis" to watch.endUtcMillis))
        return notStarted(SnipeOutcome.NOT_PLACED, reason)
    }

    // At fire time - 2 s, a second after the user's edits locked: the last read of the instruction, the group rule and
    // the price check.
    private suspend fun lock(): SnipeResult? {
        checkUserChanges()?.let { return it }
        if (clock.nowUtcMillis() - instructionReadAtUtcMillis > INSTRUCTION_MAXIMUM_AGE_MILLIS) {
            // The max is the user's ceiling: without a recent read Kestrel cannot know it was not lowered or cancelled.
            return finish(
                SnipeOutcome.FAILED,
                "Kestrel could not read your latest instruction from the phone's storage, so it did not bid.",
                userReason = NeedsUserReason.BID_FAILED,
            )
        }
        checkGroup(atFire = true)?.let { return it }
        confirmTypedStillShown()?.let { return it }
        val minimum = watch.minimumNextBid()
        when (prices.isOverMax(minimum, watch.level?.price, watch.level?.bidCount, maxBid)) {
            true -> {
                alertIfOverMax()
                val needed = minimum?.let { " eBay needs at least ${it.displayText()}." } ?: ""
                return finish(
                    SnipeOutcome.PRICE_PASSED_MAX,
                    "The price passed your max ${maxBid.displayText()} before the end, so Kestrel did not bid.$needed",
                )
            }

            null -> {
                return finish(
                    SnipeOutcome.FAILED,
                    "Kestrel could not compare eBay's minimum bid with your max (different currencies), so it did not bid.",
                    userReason = NeedsUserReason.BID_FAILED,
                )
            }

            false -> {}
        }
        if (typedAmount != maxBid) {
            return finish(
                SnipeOutcome.FAILED,
                "eBay's bid field did not hold your max at the last check, so Kestrel did not bid.",
                userReason = NeedsUserReason.BID_FAILED,
            )
        }
        return null
    }

    /**
     * The press only goes through if the field shows exactly what the page verified. In the wait since typing eBay
     * may have reformatted the field or redrawn the layer, so the page looks once more (which also takes a reformatted
     * value as the one to expect) and the max is typed again if the field no longer holds it.
     */
    private suspend fun confirmTypedStillShown(): SnipeResult? {
        val currentPage = page ?: return null
        if (typedAmount != maxBid) return null
        val budget = minOf(TYPE_TIMEOUT, latestDispatchUtcMillis - clock.nowUtcMillis() - TYPE_MINIMUM_BUDGET)
        val check = callWithin(budget) { currentPage.checkTyped() }.valueOrNull()
        if (check != null && check.verified && AmountField.minorUnits(check.fieldValue) == maxBid.minor) return null
        journal.event(
            snipeId,
            TimelineKind.PROBLEM,
            "At the last look before the bid, eBay's field showed \"${check?.fieldValue}\" " +
                "(${check?.inlineError ?: "the page did not answer"}). Kestrel types your max again.",
            mapOf("fieldValue" to check?.fieldValue, "problem" to check?.problem),
        )
        return typeAndVerify(maxBid)
    }

    private suspend fun fire(): SnipeResult? {
        // Posted now: building and posting a notification between the precise wait and the tap would delay the tap.
        progress(WindowPhase.BIDDING)
        clock.waitPreciselyUntil(fireAtUtcMillis)
        if (clock.nowUtcMillis() > latestDispatchUtcMillis) {
            return finish(
                SnipeOutcome.FAILED,
                "The phone was too late to send the bid before the end, so Kestrel did not bid.",
                userReason = NeedsUserReason.BID_FAILED,
            )
        }
        val currentPage =
            page ?: return finish(SnipeOutcome.FAILED, "Kestrel's browser page is gone, so it could not bid.")
        val endAtFire = watch.endUtcMillis
        // One press per page and per window: an unreadable reply is followed on the page, never pressed again.
        return submitAndAwaitReply(currentPage, endAtFire)
    }

    private suspend fun submitAndAwaitReply(
        currentPage: BidPage,
        endAtFire: Long,
    ): SnipeResult? {
        val notSent =
            sender.submit(currentPage, maxBid, fireAtUtcMillis, endAtFire, latestDispatchUtcMillis) {
                snipe =
                    writer.update(snipeId, snipe, PRE_TAP_STORE_TIMEOUT_MILLIS) {
                        it.copy(status = SnipeStatus.BID_SENT)
                    }
            }
        if (notSent != null) return finishNotSent(notSent)
        storeBidSentIfMissed()
        progress(WindowPhase.BID_SENT)
        when (val reply = sender.awaitReply(currentPage)) {
            is BidReply.Leading -> {
                setStatus(SnipeStatus.LEADING)
                progress(WindowPhase.LEADING)
            }

            is BidReply.Outbid -> {
                setStatus(SnipeStatus.OUTBID)
                progress(WindowPhase.OUTBID)
            }

            is BidReply.NeedsUser -> {
                handOffAfterPress(currentPage, reply)
            }

            else -> {}
        }
        return null
    }

    /**
     * eBay put a check between the press and the bid. Nothing answers it (ARCHITECTURE rule 4); the user hears about
     * it at once, while there may be time to complete it, and the window follows the auction as for any sent bid.
     */
    private suspend fun handOffAfterPress(
        currentPage: BidPage,
        reply: BidReply.NeedsUser,
    ) {
        val text =
            "eBay asked for something after Kestrel pressed bid (\"${reply.text}\"). " +
                "Open the listing to complete it; the bid may not count until you do."
        val pageReport = callWithin(REPORT_TIMEOUT) { currentPage.report() }.valueOrNull()
        journal.event(
            snipeId,
            TimelineKind.NEEDS_USER,
            text,
            mapOf("reason" to reply.reason.name, "pageReport" to pageReport),
        )
        journal.notify { needsUser(snipe, reply.reason, text) }
    }

    /**
     * The pre-tap BID_SENT write has a short limit so it cannot delay the tap; when it missed, the store still says
     * BIDDING, which the platform reads as "no bid" (a false alert, a group member let through). Written again now,
     * without the hurry.
     */
    private suspend fun storeBidSentIfMissed() {
        val stored = callWithin(STORE_TIMEOUT) { store.snipe(snipeId) }.valueOrNull() ?: return
        if (stored.status in BID_SENT_STATUSES) return
        snipe = writer.update(snipeId, snipe) { it.copy(status = withBidSent(it.status)) }
    }

    /**
     * A status that says a bid may be with eBay, whenever one may be. A cancel that came too late for the bid gives
     * way too, as the store allows: the bid may be at eBay.
     */
    private fun withBidSent(status: SnipeStatus): SnipeStatus {
        val open = !status.isFinal || status == SnipeStatus.CANCELLED
        return if (bid.sent && status !in BID_SENT_STATUSES && open) SnipeStatus.BID_SENT else status
    }

    /**
     * The page refused the press before pressing anything, so eBay never saw a bid. The final FAILED result replaces
     * the BID_SENT stored before the tap, and the user hears why at once, while there may still be time to bid.
     */
    private suspend fun finishNotSent(notSent: NotSent): SnipeResult {
        val why = notSent.reason.replaceFirstChar { it.lowercase() }
        return if (notSent.needsUser) {
            finish(
                SnipeOutcome.FAILED,
                "eBay needs you before it accepts a bid ($why), so Kestrel did not bid. Nothing was sent to eBay.",
                userReason = NeedsUserReason.VERIFICATION,
            )
        } else {
            finish(
                SnipeOutcome.FAILED,
                "Kestrel did not press eBay's bid button ($why), so no bid was sent.",
                userReason = NeedsUserReason.BID_FAILED,
            )
        }
    }

    /** Follows the auction to its true end, which Extended Bidding can move again and again. */
    private suspend fun followToEnd() {
        val stopAt = originalEndUtcMillis + MAXIMUM_FOLLOW_AFTER_END
        while (!watch.endedSeen) {
            val currentPage = page ?: return
            val now = clock.nowUtcMillis()
            if (now >= stopAt) {
                journal.event(
                    snipeId,
                    TimelineKind.PROBLEM,
                    "The auction kept extending; Kestrel stopped following it after 20 minutes.",
                )
                return
            }
            // No "ended" reply well after the end: the reload decides.
            if (now > watch.endUtcMillis + POST_END_GRACE) return
            if (poller.consecutiveFailures >= MAXIMUM_REFRESH_FAILURES) {
                journal.event(
                    snipeId,
                    TimelineKind.PROBLEM,
                    "Kestrel lost eBay's live updates and reads the result from the page instead.",
                )
                return
            }
            poller.pollIfDue(currentPage, deadlineUtcMillis = null)?.let { recordUpdate(watch.apply(it)) }
            val wait = poller.millisUntilDue
            if (wait > 0) delay(wait)
        }
    }

    private suspend fun resolve(): SnipeResult {
        progress(WindowPhase.RESOLVING)
        closeLayerIfUnused()
        val settle = stableBetween(RESOLVE_WAIT_MINIMUM, RESOLVE_WAIT_MAXIMUM, snipeId, watch.endUtcMillis)
        // However often the auction extends, the window gives up following it at one fixed point.
        val giveUpAt = originalEndUtcMillis + MAXIMUM_FOLLOW_AFTER_END + RESOLVE_WAIT_MAXIMUM
        for (attempt in 1..RESOLVE_ATTEMPTS) {
            val firstRead = attempt == 1
            val readAt =
                if (firstRead) {
                    maxOf(clock.nowUtcMillis(), watch.endUtcMillis) + settle
                } else {
                    clock.nowUtcMillis() +
                        RESOLVE_RETRY
                }
            if (readAt > giveUpAt) return stillRunning()
            clock.waitUntil(readAt, HOLD_TICK * 5)
            val listing = reloadForResult()
            if (listing != null && listing.status == ListingStatus.ENDED) return finishResolved(listing)
            if (listing != null && listing.endTimeUtcMillis > clock.nowUtcMillis()) {
                // Still running: an extension the live updates missed. Follow it again, then read once more.
                val previousEnd = watch.endUtcMillis
                watch.noteLaterEnd(listing.endTimeUtcMillis)
                if (watch.endUtcMillis > previousEnd) onExtended(previousEnd)
                followToEnd()
                val afterEnd = maxOf(clock.nowUtcMillis(), watch.endUtcMillis) + settle
                if (afterEnd > giveUpAt) return stillRunning()
                clock.waitUntil(afterEnd, HOLD_TICK * 5)
            }
        }
        return finish(
            SnipeOutcome.UNKNOWN,
            "Kestrel could not read the final result from eBay. Check the listing on eBay.",
        )
    }

    // The bid is out but the auction runs on (repeated extensions): an UNKNOWN result lets a later check read it.
    private suspend fun stillRunning(): SnipeResult =
        finish(
            SnipeOutcome.UNKNOWN,
            "The auction is still running after many extensions. Kestrel stopped following it; the next check reads " +
                "the result.",
        )

    private suspend fun reloadForResult(timeoutMillis: Long = OPEN_ITEM_TIMEOUT): Listing? {
        val currentPage = page ?: replacePage() ?: return null
        val call = callWithin(timeoutMillis) { currentPage.openItem(snipe.ref) }
        val state = call.valueOrNull()
        if (state == null) {
            journal.event(snipeId, TimelineKind.PROBLEM, "${call.failureText("Reloading the item page")}.")
            // A page that throws is probably dead (renderer gone): one fresh page for the result.
            if (call is Call.Threw) replacePage()
            return null
        }
        // eBay's browser check clears itself; a reload would start it again, so it is waited out in place.
        val cleared =
            if (state.kind == PageKind.CHALLENGE) {
                currentPage.waitOutChallenge(clock, minOf(WindowTimings.CHALLENGE_WAIT, timeoutMillis))
            } else {
                null
            }
        if (state.kind != PageKind.NORMAL && cleared == null) {
            journal.event(
                snipeId,
                TimelineKind.PROBLEM,
                "eBay showed \"${state.title}\" instead of the item while Kestrel read the result.",
            )
            return null
        }
        val listing =
            cleared
                ?: state.listing
                ?: callWithin(WindowTimings.READ_LISTING_TIMEOUT) { currentPage.readListing() }.valueOrNull()
                ?: return null
        // The check's own address says nothing about the item, so only the listing itself is compared.
        val otherItem = listing.otherItemThan(snipe.ref, if (cleared != null) snipe.ref.url else state.url)
        if (otherItem != null) {
            journal.event(
                snipeId,
                TimelineKind.PROBLEM,
                "eBay showed another item number ($otherItem) while Kestrel read the result.",
            )
            return null
        }
        return listing
    }

    private suspend fun replacePage(): BidPage? {
        if (pageReplaced) return null
        pageReplaced = true
        closePage()
        page = callWithin(CREATE_PAGE_TIMEOUT) { pages.create() }.valueOrNull()
        return page
    }

    private suspend fun finishResolved(listing: Listing): SnipeResult {
        val now = clock.nowUtcMillis()
        val extended = watch.extensions > 0 || listing.extendedBidding
        snipe = writer.update(snipeId, snipe) { keepExtendedFlag(it.withListing(listing, now), it, extended) }
        val bannerNote = listing.endedText?.let { ": \"$it\"" } ?: "."
        journal.event(
            snipeId,
            TimelineKind.ENDED,
            "The auction ended at ${listing.currentPrice.displayText()}$bannerNote",
            mapOf(
                "finalPrice" to listing.currentPrice,
                "bidCount" to listing.bidCount,
                "endUtcMillis" to listing.endTimeUtcMillis,
            ),
        )
        val resolution =
            ResultResolver.resolve(listing, if (bidAmountKnown) maxBid else null, bid.evidence(), originalEndUtcMillis)
        // Without a win, a refused bid is the story: show eBay's reason rather than a plain loss.
        val refused = resolution.outcome != SnipeOutcome.WON && bid.standing == BidStanding.REJECTED
        return finish(
            if (refused) SnipeOutcome.FAILED else resolution.outcome,
            if (refused) "eBay refused the bid: \"${bid.replyText}\"" else resolution.reason,
            finalPrice = listing.currentPrice,
            // eBay's reply to the bid only: the ended page's banner is not an answer to it (the timeline keeps it).
            ebayText = bid.replyText,
        )
    }

    private suspend fun checkGroup(atFire: Boolean): SnipeResult? {
        // The group compares fire times, so it needs the end as the window knows it now.
        val decision = groups.decide(snipe.copy(endTimeUtcMillis = watch.endUtcMillis), atFire)
        return if (decision is GroupPolicy.Decision.Skip) {
            finish(
                SnipeOutcome.SKIPPED_BY_GROUP,
                decision.reason,
            )
        } else {
            null
        }
    }

    /**
     * Ends the window: stores the result and status, writes the timeline, and notifies the user (plus a hand-off
     * notification when [userReason] says the user has something to do).
     */
    private suspend fun finish(
        outcome: SnipeOutcome,
        reason: String,
        finalPrice: Money? = watch.level?.price ?: snipe.lastPrice,
        ebayText: String? = bid.replyText,
        userReason: NeedsUserReason? = null,
        report: String? = null,
        status: SnipeStatus? = null,
    ): SnipeResult {
        closeLayerIfUnused()
        if (userReason != null) {
            val pageReport = report ?: callWithin(REPORT_TIMEOUT) { page?.report() }.valueOrNull()
            journal.event(
                snipeId,
                TimelineKind.NEEDS_USER,
                reason,
                mapOf(
                    "reason" to userReason.name,
                    "pageReport" to pageReport,
                ),
            )
        }
        val snipeResult =
            SnipeResult(
                outcome = outcome,
                finalPrice = finalPrice,
                bidSentAtUtcMillis = bid.sentAtUtcMillis,
                bidLeadMillis = bid.leadMillis,
                replyLatencyMillis = bid.replyLatencyMillis,
                ebayReplyText = ebayText,
                reason = reason,
            )
        val finalStatus = status ?: statusAfter(outcome)
        snipe =
            writer.update(snipeId, snipe) {
                it.copy(status = finalStatus ?: withBidSent(it.status), result = snipeResult)
            }
        journal.event(
            snipeId,
            if (outcome == SnipeOutcome.CANCELLED) TimelineKind.CANCELLED else TimelineKind.RESULT,
            reason,
            mapOf(
                "outcome" to outcome.name,
                "finalPrice" to finalPrice,
                "bidSentAtUtcMillis" to bid.sentAtUtcMillis,
                "bidLeadMillis" to bid.leadMillis,
                "replyLatencyMillis" to bid.replyLatencyMillis,
                "ebayReplyText" to ebayText,
                "submits" to bid.submits,
                "extensions" to watch.extensions,
            ),
        )
        journal.notify { result(snipe, snipeResult) }
        // After the result, which clears the snipe's alerts: the hand-off must outlive it (ARCHITECTURE rule 4).
        if (userReason != null && watch.endUtcMillis > clock.nowUtcMillis()) {
            if (bid.sentAtUtcMillis == null) {
                journal.notify { bidLeftToUser(snipe, userReason, reason) }
            } else {
                journal.notify { needsUser(snipe, userReason, reason) }
            }
        }
        if (outcome == SnipeOutcome.WON) groups.cancelRestAfterWin(snipe)
        return snipeResult
    }

    // The user deleted the snipe: nothing to store or notify, just stop without bidding.
    private suspend fun finishDeleted(): SnipeResult {
        closeLayerIfUnused()
        return notStarted(SnipeOutcome.CANCELLED, "The snipe was deleted before the bid.")
    }

    // Null keeps the current status: a sent bid whose result is unknown stays in its bidding status for a later check.
    private fun statusAfter(outcome: SnipeOutcome): SnipeStatus? =
        when (outcome) {
            SnipeOutcome.WON -> SnipeStatus.WON
            SnipeOutcome.LOST, SnipeOutcome.PRICE_PASSED_MAX -> SnipeStatus.LOST
            SnipeOutcome.ENDED_EARLY -> SnipeStatus.ENDED_EARLY
            SnipeOutcome.CANCELLED -> SnipeStatus.CANCELLED
            SnipeOutcome.SKIPPED_BY_GROUP -> SnipeStatus.SKIPPED_BY_GROUP
            SnipeOutcome.NOT_PLACED, SnipeOutcome.FAILED -> SnipeStatus.FAILED
            SnipeOutcome.UNKNOWN -> if (bid.sent) null else SnipeStatus.FAILED
        }

    private suspend fun setStatus(status: SnipeStatus) {
        snipe = writer.update(snipeId, snipe) { it.copy(status = status) }
    }

    private fun progress(
        phase: WindowPhase,
        detail: String? = null,
    ) {
        journal.notify { windowProgress(snipe, phase, detail) }
    }

    // A layer opened but not used is closed like a person would close it; after a bid it stays as eBay left it.
    private suspend fun closeLayerIfUnused() {
        if (!layerOpen || bid.sent) return
        layerOpen = false
        val currentPage = page ?: return
        callWithin(CLOSE_LAYER_TIMEOUT) { currentPage.closeLayer() }
    }

    private fun closePage() {
        val currentPage = page ?: return
        page = null
        try {
            currentPage.close()
        } catch (ignored: Exception) {
            // The page is being thrown away; a failure to close it changes nothing for the bid.
        }
    }

    private companion object {
        const val LAYER_ATTEMPTS = 2
        const val TYPE_ATTEMPTS = 2

        /** How long after typing Kestrel waits for eBay to enable its bid button before typing again. */
        const val BID_BUTTON_WAIT_MILLIS = 6_000L

        /** A dead page is replaced only with this much time left: load, open the layer and type on a new page. */
        const val REBUILD_MINIMUM_MILLIS = 15_000L

        /** The instruction must have been read this recently at the lock. */
        const val INSTRUCTION_MAXIMUM_AGE_MILLIS = 5_000L

        /**
         * The limit on each of the two store calls (a read and a write) that put BID_SENT down right before the tap. A
         * call blocked in I/O can outlast it, so the sender checks the latest dispatch time again after them.
         */
        const val PRE_TAP_STORE_TIMEOUT_MILLIS = 150L

        /** Statuses a window can hand back when it closes early to be scheduled again. */
        val RESUMABLE_STATUSES =
            setOf(
                SnipeStatus.ARMED,
                SnipeStatus.PRICE_OVER_MAX,
                SnipeStatus.NEEDS_SIGN_IN,
                SnipeStatus.NEEDS_YOU,
                SnipeStatus.DRY_RUN_FAILED,
            )

        /** A result for a window that stores nothing (the snipe is gone, finished, or not ready to bid). */
        fun notStarted(
            outcome: SnipeOutcome,
            reason: String,
        ): SnipeResult = SnipeResult(outcome, null, null, null, null, null, reason)

        // The ended page's flag can read false even though the window saw the auction extend; keep what was seen.
        fun keepExtendedFlag(
            updated: Snipe,
            previous: Snipe,
            seenExtending: Boolean,
        ): Snipe = updated.copy(extendedBidding = previous.extendedBidding || seenExtending)

        fun outcomeOfFinishedStatus(status: SnipeStatus): SnipeOutcome =
            when (status) {
                SnipeStatus.WON -> SnipeOutcome.WON
                SnipeStatus.LOST -> SnipeOutcome.LOST
                SnipeStatus.ENDED_EARLY -> SnipeOutcome.ENDED_EARLY
                SnipeStatus.CANCELLED -> SnipeOutcome.CANCELLED
                SnipeStatus.SKIPPED_BY_GROUP -> SnipeOutcome.SKIPPED_BY_GROUP
                else -> SnipeOutcome.FAILED
            }
    }
}
