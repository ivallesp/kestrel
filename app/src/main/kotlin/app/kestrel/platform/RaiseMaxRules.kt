package app.kestrel.platform

import app.kestrel.model.Money

internal sealed interface RaiseMaxDecision {
    data class Accept(
        val newMax: Money,
    ) : RaiseMaxDecision

    data class Reject(
        val message: String,
    ) : RaiseMaxDecision
}

/**
 * Checks a max typed into the notification. It must be a real amount in the listing's currency, above the current
 * max, at least eBay's minimum, and at most ten times the current price: a reply field has no confirmation step, so
 * an extra zero must not become a bid.
 */
internal class RaiseMaxRules(
    private val parseAmount: (text: String, currency: String) -> Money?,
    private val formatMoney: (Money) -> String,
) {
    fun decide(
        typed: String?,
        currency: String,
        currentMax: Money?,
        currentPrice: Money?,
        minimum: Money?,
    ): RaiseMaxDecision {
        val text = typed?.trim().orEmpty()
        val amount = if (text.isEmpty()) null else parse(text, currency)
        val max = currentMax?.takeIf { it.currency == currency }
        val floor = minimum?.takeIf { it.currency == currency }
        val reference = (currentPrice?.takeIf { it.currency == currency } ?: floor)?.takeIf { it.minor > 0 }
        val problem =
            when {
                text.isEmpty() -> {
                    "Type the new max, for example 52.50."
                }

                amount == null -> {
                    "Couldn't read \"$text\". Type an amount like 52.50."
                }

                amount.currency != currency -> {
                    "Type the amount in $currency."
                }

                amount.minor <= 0 -> {
                    "The max must be more than zero."
                }

                max != null && amount <= max -> {
                    "That isn't more than your max of ${formatMoney(max)}."
                }

                floor != null && amount < floor -> {
                    "eBay needs at least ${formatMoney(floor)}."
                }

                reference != null && amount.minor > reference.minor * MAX_PRICE_MULTIPLE -> {
                    "That is more than $MAX_PRICE_MULTIPLE times the current price. Open Kestrel to set it."
                }

                else -> {
                    null
                }
            }
        if (problem != null) return RaiseMaxDecision.Reject(problem)
        return RaiseMaxDecision.Accept(checkNotNull(amount))
    }

    private fun parse(
        text: String,
        currency: String,
    ): Money? =
        try {
            parseAmount(text, currency)
        } catch (error: RuntimeException) {
            null
        }

    companion object {
        const val MAX_PRICE_MULTIPLE = 10
    }
}
