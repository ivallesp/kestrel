package app.kestrel.data

import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus

sealed interface AddResult {
    data class Added(
        val snipe: Snipe,
    ) : AddResult

    /** The listing already has an open snipe; nothing was added. */
    data class AlreadyAdded(
        val snipe: Snipe,
    ) : AddResult

    data class Rejected(
        val reason: String,
    ) : AddResult
}

sealed interface EditResult {
    data class Done(
        val snipe: Snipe,
    ) : EditResult

    data object NotFound : EditResult

    data class NotAllowed(
        val reason: String,
    ) : EditResult
}

/** Totals over finished snipes. Amounts are per currency, because a user can bid on several sites. */
data class SnipeStats(
    val wins: Int,
    val losses: Int,
    val totalPaid: List<Money>,
    val totalSavedUnderMax: List<Money>,
) {
    companion object {
        fun of(finished: List<Snipe>): SnipeStats {
            val won = finished.filter { it.status == SnipeStatus.WON }
            val paid = won.mapNotNull { it.result?.finalPrice }
            val saved =
                won.mapNotNull { snipe ->
                    val finalPrice = snipe.result?.finalPrice ?: return@mapNotNull null
                    val max = snipe.maxBid ?: return@mapNotNull null
                    if (max.currency != finalPrice.currency || max < finalPrice) null else max - finalPrice
                }
            return SnipeStats(
                wins = won.size,
                losses = finished.count { it.status == SnipeStatus.LOST },
                totalPaid = sumPerCurrency(paid),
                totalSavedUnderMax = sumPerCurrency(saved),
            )
        }

        private fun sumPerCurrency(amounts: List<Money>): List<Money> =
            amounts
                .groupBy { it.currency }
                .map { (currency, sameCurrency) -> Money(sameCurrency.sumOf { it.minor }, currency) }
                .sortedBy { it.currency }
    }
}
