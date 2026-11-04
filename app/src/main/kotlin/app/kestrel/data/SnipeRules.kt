package app.kestrel.data

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

    /** Max, lead and group may change before the window opens, never during it or after. */
    fun editRefusal(status: SnipeStatus): String? =
        when {
            status.isFinal -> "This snipe has already finished."
            status.family == StatusFamily.BIDDING -> "The bid window is open, so this can't change now."
            else -> null
        }

    fun cancelRefusal(status: SnipeStatus): String? =
        when {
            status.isFinal -> "This snipe has already finished."
            status in bidInFlight -> "eBay already has your bid, so Kestrel can't cancel it."
            else -> null
        }

    fun deleteRefusal(status: SnipeStatus): String? =
        if (!status.isFinal && status.family == StatusFamily.BIDDING) {
            "The bid window is open. Cancel the snipe first."
        } else {
            null
        }

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
     * - once the window opened, the snipe never returns to "not started".
     */
    fun acceptsEngineStatus(
        stored: Snipe,
        incoming: Snipe,
    ): Boolean {
        if (incoming.status == stored.status) return true
        if (stored.maxBid == null && incoming.status.needsMax) return false
        if (stored.maxBid != null && incoming.status == SnipeStatus.WATCHING) return false
        return when {
            stored.status == SnipeStatus.CANCELLED -> provesBidWasSent(incoming)
            stored.status.isFinal -> incoming.status.isFinal
            stored.status in bidInFlight -> incoming.status in bidInFlight || incoming.status.isFinal
            stored.status.family == StatusFamily.BIDDING -> incoming.status !in notStarted
            else -> true
        }
    }

    /** Statuses that only make sense with a bidding instruction; sign-in and "needs you" can happen while watching. */
    private val SnipeStatus.needsMax: Boolean get() = this in statusesNeedingMax

    private val statusesNeedingMax =
        bidInFlight +
            setOf(SnipeStatus.ARMED, SnipeStatus.PRICE_OVER_MAX, SnipeStatus.DRY_RUN_FAILED, SnipeStatus.BIDDING)

    private fun provesBidWasSent(snipe: Snipe): Boolean =
        snipe.status in bidInFlight ||
            (snipe.status in setOf(SnipeStatus.WON, SnipeStatus.LOST) && snipe.result?.bidSentAtUtcMillis != null)
}
