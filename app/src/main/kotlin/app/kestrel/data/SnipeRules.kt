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
     * a cancelled snipe stays cancelled unless a bid provably went out, a finished snipe never becomes active again,
     * and once the window opened the snipe never returns to "not started".
     */
    fun acceptsEngineStatus(
        stored: Snipe,
        incoming: Snipe,
    ): Boolean {
        if (incoming.status == stored.status) return true
        return when {
            stored.status == SnipeStatus.CANCELLED -> provesBidWasSent(incoming)
            stored.status.isFinal -> incoming.status.isFinal
            stored.status.family == StatusFamily.BIDDING -> incoming.status !in notStarted
            else -> true
        }
    }

    private fun provesBidWasSent(snipe: Snipe): Boolean =
        snipe.status in bidInFlight ||
            (snipe.status in setOf(SnipeStatus.WON, SnipeStatus.LOST) && snipe.result?.bidSentAtUtcMillis != null)
}
