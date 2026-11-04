package app.kestrel.engine

import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.StatusFamily
import app.kestrel.model.TimelineKind

/** One background check of a listing. See [ListingCheck]. */
internal class ListingCheckProcedure(
    private val store: SnipeStore,
    notifier: SnipeNotifier,
    private val clock: EngineClock,
    minimumBidRule: MinimumBidRule,
    private val windowRunningHere: ((snipeId: Long) -> Boolean)? = null,
) {
    private val journal = EngineJournal(store, notifier, clock)
    private val writer = SnipeWriter(store, clock)
    private val prices = PriceGuard(minimumBidRule)
    private val groups = GroupGate(store, writer, journal)

    suspend fun check(
        snipeId: Long,
        preview: BidLayerPreview,
    ): CheckOutcome {
        val read = callWithin(WindowTimings.STORE_TIMEOUT) { store.snipe(snipeId) }
        if (read !is Call.Done) return CheckOutcome.Failed(read.failureText("Reading the snipe"))
        val snipe = read.value ?: return CheckOutcome.Failed("The snipe no longer exists.")
        if (snipe.status.isFinal) return CheckOutcome.Ended(snipe)
        // A bidding status with a result is a window that finished without a final answer (UNKNOWN). Without a
        // result the window may still be running, unless the end is long past.
        val windowRunning = snipe.status.family == StatusFamily.BIDDING
        val windowMayBeRunning =
            windowRunning && snipe.result == null &&
                (
                    windowRunningHere?.invoke(snipe.id)
                        ?: (clock.nowUtcMillis() < snipe.endTimeUtcMillis + ABANDONED_WINDOW_AFTER_END_MILLIS)
                )
        if (windowMayBeRunning) {
            return CheckOutcome.Failed("The bid window is running and follows the listing itself.")
        }
        if (!windowRunning) groups.skipIfComplete(snipe)?.let { return CheckOutcome.Ended(it) }
        val load = callWithin(WindowTimings.OPEN_ITEM_TIMEOUT) { preview.openItem(snipe.ref) }
        val page = load.valueOrNull() ?: return failed(snipe, "${load.failureText("Loading the listing")}. $TRY_LATER")
        return when (page.kind) {
            PageKind.NORMAL -> {
                val listing =
                    page.listing
                        ?: callWithin(WindowTimings.READ_LISTING_TIMEOUT) { preview.readListing() }.valueOrNull()
                        ?: return failed(snipe, "The item page did not include the listing details. $TRY_LATER")
                onListing(snipe, listing, page.url, windowRunning)
            }

            PageKind.RATE_WALL -> {
                failed(snipe, "$RATE_WALL_TEXT. $TRY_LATER")
            }

            PageKind.CHALLENGE -> {
                // eBay's check clears itself; a reload would start it again, so it is waited out in place.
                val listing =
                    preview.waitOutChallenge(clock, WindowTimings.CHALLENGE_WAIT)
                        ?: return failed(snipe, "$CHALLENGE_TEXT. $TRY_LATER")
                onListing(snipe, listing, snipe.ref.url, windowRunning)
            }

            PageKind.SIGN_IN, PageKind.THIRD_PARTY_SIGN_IN, PageKind.CAPTCHA, PageKind.OTHER, PageKind.NOT_FOUND -> {
                val handOff =
                    userHandOffFor(page.kind, page.title) ?: UserHandOff(NeedsUserReason.UNKNOWN_PAGE, page.title)
                handToUser(snipe, handOff, page.kind, windowRunning)
            }
        }
    }

    private suspend fun onListing(
        snipe: Snipe,
        listing: Listing,
        pageUrl: String,
        windowRunning: Boolean,
    ): CheckOutcome {
        val otherItem = listing.otherItemThan(snipe.ref, pageUrl)
        if (otherItem != null) {
            return if (windowRunning) {
                failed(snipe, "eBay now shows another item number ($otherItem). Check the result of your bid on eBay.")
            } else {
                endAsRelisted(snipe, otherItem)
            }
        }
        if (windowRunning) return resolveAbandonedWindow(snipe, listing)
        if (listing.status == ListingStatus.ENDED) {
            return endListing(snipe, listing, BidEvidence(sent = false, standing = BidStanding.UNKNOWN))
        }
        return updateRunning(snipe, listing)
    }

    private suspend fun updateRunning(
        snipe: Snipe,
        listing: Listing,
    ): CheckOutcome {
        val now = clock.nowUtcMillis()
        val max = snipe.maxBid
        val minimum = prices.minimumNextBid(listing)
        val overMax = max?.let { prices.isOverMax(minimum, listing.currentPrice, listing.bidCount, it) }
        val status = statusAfterCheck(snipe, listing, overMax)
        val updated =
            writer.updateIfIdle(snipe.id) { it.withListing(listing, now).copy(status = status) }
                ?: return leftAlone(snipe.id)
        callWithin(WindowTimings.STORE_TIMEOUT) {
            store.recordPrice(snipe.id, now, listing.currentPrice, listing.bidCount)
        }
        val statusChanged = status != snipe.status
        if (statusChanged && status == SnipeStatus.NEEDS_SIGN_IN) {
            alertUser(
                updated,
                NeedsUserReason.SIGN_IN,
                "eBay shows you as signed out. Sign in to eBay in Kestrel again before the end.",
            )
        }
        if (statusChanged && status == SnipeStatus.NEEDS_YOU && max != null) {
            alertUser(
                updated,
                NeedsUserReason.BID_FAILED,
                "Your max is in ${max.currency}, but this listing takes bids in ${listing.currentPrice.currency}. " +
                    "Set the max again in ${listing.currentPrice.currency}.",
            )
        }
        val sameLevel = snipe.lastPrice == listing.currentPrice && snipe.lastBidCount == listing.bidCount
        if (max != null && overMax == true && !(sameLevel && snipe.status == SnipeStatus.PRICE_OVER_MAX)) {
            journal.event(
                snipe.id,
                TimelineKind.PRICE_OVER_MAX,
                overMaxText(minimum, listing.currentPrice, max),
                mapOf(
                    "price" to listing.currentPrice,
                    "minimum" to minimum,
                    "max" to max,
                    "bidCount" to listing.bidCount,
                ),
            )
            journal.notify { priceOverMax(updated, listing.currentPrice, minimum) }
        }
        val changes = describeChanges(snipe, listing)
        if (changes.isNotEmpty() || statusChanged) {
            journal.event(
                snipe.id,
                TimelineKind.CHECKED,
                "Checked the listing: ${changes.ifEmpty { listOf("no change on eBay") }.joinToString("; ")}.",
                mapOf(
                    "price" to listing.currentPrice,
                    "bidCount" to listing.bidCount,
                    "endUtcMillis" to listing.endTimeUtcMillis,
                ),
            )
        }
        return CheckOutcome.Updated(updated)
    }

    // The status follows what the page shows now, so a problem that went away (a raised max, a solved captcha) clears.
    private fun statusAfterCheck(
        snipe: Snipe,
        listing: Listing,
        overMax: Boolean?,
    ): SnipeStatus =
        when {
            snipe.isWatchOnly -> SnipeStatus.WATCHING
            listing.signedIn == false -> SnipeStatus.NEEDS_SIGN_IN
            snipe.status == SnipeStatus.NEEDS_SIGN_IN && listing.signedIn != true -> SnipeStatus.NEEDS_SIGN_IN
            overMax == null -> SnipeStatus.NEEDS_YOU
            overMax == true -> SnipeStatus.PRICE_OVER_MAX
            snipe.dryRunPassed == false -> SnipeStatus.DRY_RUN_FAILED
            else -> SnipeStatus.ARMED
        }

    private fun describeChanges(
        snipe: Snipe,
        listing: Listing,
    ): List<String> =
        buildList {
            if (snipe.lastPrice != listing.currentPrice || snipe.lastBidCount != listing.bidCount) {
                add("price ${listing.currentPrice.displayText()} with ${listing.bidCount} bids")
            }
            if (snipe.endTimeUtcMillis != listing.endTimeUtcMillis) add("the end time moved")
            if (snipe.buyItNow == null && listing.buyItNow != null) {
                add(
                    "Buy It Now is available at ${listing.buyItNow.displayText()}, so someone could end the auction at once",
                )
            }
            if (!snipe.extendedBidding && listing.extendedBidding) add("this auction uses extended bidding")
        }

    private suspend fun endListing(
        snipe: Snipe,
        listing: Listing,
        evidence: BidEvidence,
        resolvingAbandonedWindow: Boolean = false,
    ): CheckOutcome {
        val now = clock.nowUtcMillis()
        // After a bid the stored max is not proof of the amount bid (it may have been edited since), so the price is
        // not compared with it: only eBay's own fields can then decide.
        val comparableMax = if (evidence.sent) null else snipe.maxBid
        val resolution = ResultResolver.resolve(listing, comparableMax, evidence, snipe.endTimeUtcMillis)
        val missedBid = resolution.outcome == SnipeOutcome.NOT_PLACED && !snipe.isWatchOnly
        val reason =
            when {
                snipe.isWatchOnly && resolution.outcome == SnipeOutcome.NOT_PLACED -> {
                    "You were watching this auction. It ended at ${listing.currentPrice.displayText()}."
                }

                missedBid -> {
                    "The auction ended without a bid from Kestrel: the bid window did not run."
                }

                else -> {
                    resolution.reason
                }
            }
        val status =
            when (resolution.outcome) {
                SnipeOutcome.WON -> SnipeStatus.WON
                SnipeOutcome.ENDED_EARLY -> SnipeStatus.ENDED_EARLY
                SnipeOutcome.LOST -> SnipeStatus.LOST
                SnipeOutcome.NOT_PLACED -> if (missedBid) SnipeStatus.FAILED else SnipeStatus.LOST
                SnipeOutcome.UNKNOWN -> snipe.status
                else -> SnipeStatus.FAILED
            }
        val snipeResult =
            SnipeResult(
                outcome = resolution.outcome,
                finalPrice = listing.currentPrice,
                bidSentAtUtcMillis = null,
                bidLeadMillis = null,
                replyLatencyMillis = null,
                ebayReplyText = listing.endedText,
                reason = reason,
            )
        val repeatUnknown = resolution.outcome == SnipeOutcome.UNKNOWN && snipe.result?.outcome == SnipeOutcome.UNKNOWN
        val change = { current: Snipe -> current.withListing(listing, now).copy(status = status, result = snipeResult) }
        val updated =
            if (resolvingAbandonedWindow) {
                writer.update(snipe.id, snipe, change = change)
            } else {
                writer.updateIfIdle(snipe.id, change) ?: return leftAlone(snipe.id)
            }
        journal.event(
            snipe.id,
            TimelineKind.ENDED,
            "The auction ended at ${listing.currentPrice.displayText()}.",
            mapOf(
                "endedText" to listing.endedText,
                "finalPrice" to listing.currentPrice,
                "endUtcMillis" to listing.endTimeUtcMillis,
            ),
        )
        journal.event(snipe.id, TimelineKind.RESULT, reason, mapOf("outcome" to resolution.outcome.name))
        if (!repeatUnknown) journal.notify { result(updated, snipeResult) }
        return if (status.isFinal) CheckOutcome.Ended(updated) else CheckOutcome.Updated(updated)
    }

    // A window that died (process killed) leaves its bidding status behind; read the result it could not read.
    private suspend fun resolveAbandonedWindow(
        snipe: Snipe,
        listing: Listing,
    ): CheckOutcome {
        if (listing.status == ListingStatus.ACTIVE) {
            val now = clock.nowUtcMillis()
            return CheckOutcome.Updated(writer.update(snipe.id, snipe) { it.withListing(listing, now) })
        }
        val standing =
            when (snipe.status) {
                SnipeStatus.LEADING -> BidStanding.LEADING
                SnipeStatus.OUTBID -> BidStanding.OUTBID
                else -> BidStanding.UNKNOWN
            }
        val evidence = BidEvidence(sent = snipe.status in BID_SENT_STATUSES, standing = standing)
        return endListing(snipe, listing, evidence, resolvingAbandonedWindow = true)
    }

    private suspend fun endAsRelisted(
        snipe: Snipe,
        otherItemId: String,
    ): CheckOutcome {
        val now = clock.nowUtcMillis()
        val beforeEnd = now < snipe.endTimeUtcMillis
        val outcome = if (beforeEnd) SnipeOutcome.ENDED_EARLY else SnipeOutcome.NOT_PLACED
        val status =
            when {
                beforeEnd -> SnipeStatus.ENDED_EARLY
                snipe.isWatchOnly -> SnipeStatus.LOST
                else -> SnipeStatus.FAILED
            }
        val reason =
            "eBay now shows item $otherItemId instead: the seller relisted it. Kestrel does not bid on the new " +
                "listing; add it again to snipe it."
        val snipeResult = SnipeResult(outcome, null, null, null, null, null, reason)
        // The page describes the other listing, so none of its data is copied into this snipe.
        val updated =
            writer.updateIfIdle(
                snipe.id,
            ) { it.copy(status = status, result = snipeResult, lastCheckedAtUtcMillis = now) }
                ?: return leftAlone(snipe.id)
        journal.event(snipe.id, TimelineKind.ENDED, reason, mapOf("relistedItemId" to otherItemId))
        journal.notify { result(updated, snipeResult) }
        return CheckOutcome.Ended(updated)
    }

    private suspend fun handToUser(
        snipe: Snipe,
        handOff: UserHandOff,
        kind: PageKind,
        windowRunning: Boolean,
    ): CheckOutcome {
        if (windowRunning) {
            // Reading the result of an abandoned window: nothing to change, the user has to look.
            alertUser(snipe, handOff.reason, handOff.text)
            return CheckOutcome.NeedsUser(handOff.reason)
        }
        val now = clock.nowUtcMillis()
        val status =
            if (kind == PageKind.SIGN_IN ||
                kind == PageKind.THIRD_PARTY_SIGN_IN
            ) {
                SnipeStatus.NEEDS_SIGN_IN
            } else {
                SnipeStatus.NEEDS_YOU
            }
        val updated =
            writer.updateIfIdle(snipe.id) { it.copy(status = status, lastCheckedAtUtcMillis = now) }
                ?: return leftAlone(snipe.id)
        // Alert once per problem, not on every check while it lasts.
        if (status != snipe.status) alertUser(updated, handOff.reason, handOff.text)
        return CheckOutcome.NeedsUser(handOff.reason)
    }

    private suspend fun alertUser(
        snipe: Snipe,
        reason: NeedsUserReason,
        text: String,
    ) {
        journal.event(snipe.id, TimelineKind.NEEDS_USER, text, mapOf("reason" to reason.name))
        journal.notify { needsUser(snipe, reason, text) }
    }

    private suspend fun failed(
        snipe: Snipe,
        reason: String,
    ): CheckOutcome {
        // Count the attempt as a check, so the next one follows the normal cadence instead of retrying at once.
        writer.updateIfIdle(snipe.id) { it.copy(lastCheckedAtUtcMillis = clock.nowUtcMillis()) }
        journal.event(snipe.id, TimelineKind.PROBLEM, reason)
        return CheckOutcome.Failed(reason)
    }

    // A bid window took the snipe over, or the snipe finished, while this check loaded the page: leave it alone.
    private suspend fun leftAlone(snipeId: Long): CheckOutcome {
        val current = callWithin(WindowTimings.STORE_TIMEOUT) { store.snipe(snipeId) }.valueOrNull()
        return when {
            current == null -> CheckOutcome.Failed("The snipe no longer exists.")
            current.status.isFinal -> CheckOutcome.Ended(current)
            else -> CheckOutcome.Failed("The bid window took over while the check ran.")
        }
    }

    private companion object {
        fun overMaxText(
            minimum: Money?,
            price: Money,
            max: Money,
        ): String =
            if (minimum != null) {
                "The next bid would have to be at least ${minimum.displayText()}, above your max ${max.displayText()}."
            } else {
                "The price ${price.displayText()} has reached your max ${max.displayText()}."
            }

        /** After this long past the end a bidding status means the window died without writing its result. */
        const val ABANDONED_WINDOW_AFTER_END_MILLIS = 10 * 60_000L
        const val TRY_LATER = "Kestrel will try again at the next check."
    }
}
