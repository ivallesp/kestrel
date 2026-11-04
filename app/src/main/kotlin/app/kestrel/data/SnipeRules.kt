package app.kestrel.data

import app.kestrel.engine.BidTiming
import app.kestrel.engine.WindowTimings
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import app.kestrel.model.StatusFamily

/** Which user edits are allowed in which status, and how engine writes merge with what is stored. */
internal object SnipeRules {
    val LEAD_SECONDS_RANGE = 2..60

    val FINAL_STATUS_NAMES: List<String> = SnipeStatus.entries.filter { it.isFinal }.map { it.name }

    private val bidInFlight = setOf(SnipeStatus.BID_SENT, SnipeStatus.LEADING, SnipeStatus.OUTBID, SnipeStatus.EXTENDED)
    private val notStarted = setOf(SnipeStatus.ARMED, SnipeStatus.WATCHING)

    /** Statuses that only say how the max compares with the price, so a new max decides them again. */
    private val decidedByMax = setOf(SnipeStatus.WATCHING, SnipeStatus.PRICE_OVER_MAX)

    /** The group may change before the window opens, never during it or after: the window checks it at its start. */
    fun editRefusal(status: SnipeStatus): String? =
        when {
            status.isFinal -> "This snipe has already finished."
            status.family == StatusFamily.BIDDING -> "The bid window is open, so this can't change now."
            else -> null
        }

    /**
     * The max and the lead may also change while the window is open, until the edits lock 3 s before the bid
     * ([BidTiming.editsLockAtUtcMillis], on true time): the window reads them a last time a second later. Never once
     * a bid may be with eBay or the window has a result. A new lead must leave both the old and the new lock ahead.
     * The engine's raise hint and the price alert's Raise max button follow this same rule.
     */
    fun maxOrLeadRefusal(
        snipe: Snipe,
        nowUtcMillis: Long,
        newLeadSeconds: Int? = null,
    ): String? {
        val status = snipe.status
        if (status.isFinal) return "This snipe has already finished."
        if (status.family != StatusFamily.BIDDING) return null
        if (status in bidInFlight) return "eBay already has your bid, so this can't change now."
        if (snipe.result != null) return "The bid window has run, so this can't change now."
        val leads = listOfNotNull(snipe.leadSeconds, newLeadSeconds)
        val open = leads.all { BidTiming.editsOpen(snipe.endTimeUtcMillis, it, nowUtcMillis) }
        return if (open) null else "Kestrel bids in a few seconds, so this can't change now."
    }

    fun cancelRefusal(status: SnipeStatus): String? =
        when {
            status.isFinal -> "This snipe has already finished."
            status in bidInFlight -> "eBay already has your bid, so Kestrel can't cancel it."
            else -> null
        }

    /**
     * A snipe can be deleted unless a window may be running it. Long after the end none can be, so a snipe whose
     * result Kestrel never read can be removed (which also lets its group go on).
     */
    fun deleteRefusal(
        snipe: Snipe,
        nowUtcMillis: Long,
    ): String? =
        if (!snipe.status.isFinal && snipe.status.family == StatusFamily.BIDDING && !longOver(snipe, nowUtcMillis)) {
            "The bid window is open. Cancel the snipe first."
        } else {
            null
        }

    /**
     * The user may say how a bid ended once the auction is long over and Kestrel could not read it: only for a snipe
     * whose bid may be with eBay, and only once.
     */
    fun settleRefusal(
        snipe: Snipe,
        nowUtcMillis: Long,
    ): String? =
        when {
            snipe.status.isFinal -> "This snipe has already finished."
            snipe.status !in bidInFlight && snipe.result?.bidSentAtUtcMillis == null -> "Kestrel did not bid here."
            !longOver(snipe, nowUtcMillis) -> "The auction has not ended yet, or Kestrel is still reading the result."
            else -> null
        }

    // Past this long after the end no window runs a snipe (ListingCheckProcedure takes it as abandoned too).
    private fun longOver(
        snipe: Snipe,
        nowUtcMillis: Long,
    ): Boolean = nowUtcMillis > snipe.endTimeUtcMillis + ABANDONED_WINDOW_AFTER_END_MILLIS

    private const val ABANDONED_WINDOW_AFTER_END_MILLIS = 10 * 60_000L

    /** The status a new or edited max leads to, judged against the last price Kestrel saw. */
    fun statusForMax(
        current: Snipe,
        max: Money?,
    ): SnipeStatus {
        if (max == null) return SnipeStatus.WATCHING
        if (current.status !in decidedByMax) return current.status
        val overMax = isBelowPrice(max, current.lastPrice, current.lastBidCount)
        return if (overMax) SnipeStatus.PRICE_OVER_MAX else SnipeStatus.ARMED
    }

    /** With bids the max must beat the price; without bids the opening price itself is a valid bid. */
    fun isBelowPrice(
        max: Money,
        price: Money?,
        bidCount: Int?,
    ): Boolean {
        if (price == null || price.currency != max.currency) return false
        return if ((bidCount ?: 0) > 0) max <= price else max < price
    }

    /**
     * Whether an engine write may replace the stored status and result. Engine jobs can run side by side (a window
     * and a slow background check) and each writes back its own copy, so a stale copy must not undo progress:
     * - whether a snipe bids at all follows the user's max, so a snipe with a max is never set to watching and a
     *   watched one is never armed;
     * - a cancelled snipe stays cancelled unless a bid provably went out;
     * - a finished snipe never becomes active again;
     * - once eBay has the bid, the snipe only moves on to a later bid state or a result, never back to a state
     *   where its max could be edited or a second window could open;
     * - once the window opened, the snipe never returns to "not started", unless the window handed it back because
     *   the end moved far away ([handedBack]).
     */
    fun acceptsEngineStatus(
        stored: Snipe,
        incoming: Snipe,
        nowUtcMillis: Long,
    ): Boolean {
        if (incoming.status == stored.status) return true
        if (stored.maxBid == null && incoming.status.needsMax) return false
        if (stored.maxBid != null && incoming.status == SnipeStatus.WATCHING) return false
        return when {
            stored.status == SnipeStatus.CANCELLED -> {
                provesBidWasSent(incoming)
            }

            stored.status.isFinal -> {
                incoming.status.isFinal
            }

            stored.status in bidInFlight -> {
                incoming.status in bidInFlight || incoming.status.isFinal
            }

            stored.status.family == StatusFamily.BIDDING -> {
                incoming.status !in notStarted ||
                    (handedBack(stored, nowUtcMillis) && handedBack(incoming, nowUtcMillis))
            }

            else -> {
                true
            }
        }
    }

    /**
     * True when no bid window can be holding [snipe]: it has no result and its end is further away than a window
     * ever holds the phone ([WindowTimings.MAXIMUM_HOLD_BEFORE_END]; a window hands the snipe back when the end moves
     * past that). A bidding status on such a snipe is a deliberate hand-back to the scheduler, not a stall, and a
     * resumable status may replace it without letting a stale copy reset a window that will bid.
     */
    fun handedBack(
        snipe: Snipe,
        nowUtcMillis: Long,
    ): Boolean = snipe.result == null && snipe.endTimeUtcMillis - nowUtcMillis > WindowTimings.MAXIMUM_HOLD_BEFORE_END

    /** Statuses that only make sense with a bidding instruction; sign-in and "needs you" can happen while watching. */
    private val SnipeStatus.needsMax: Boolean get() = this in statusesNeedingMax

    private val statusesNeedingMax =
        bidInFlight +
            setOf(SnipeStatus.ARMED, SnipeStatus.PRICE_OVER_MAX, SnipeStatus.DRY_RUN_FAILED, SnipeStatus.BIDDING)

    private fun provesBidWasSent(snipe: Snipe): Boolean =
        snipe.status in bidInFlight ||
            (snipe.status in setOf(SnipeStatus.WON, SnipeStatus.LOST) && snipe.result?.bidSentAtUtcMillis != null)
}
