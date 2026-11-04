package app.kestrel.ui.screens.add

import androidx.compose.runtime.Immutable
import app.kestrel.ebay.BidIncrements
import app.kestrel.ebay.MoneyText
import app.kestrel.model.EbaySite
import app.kestrel.model.Money
import app.kestrel.ui.format.formatted

/** What the typed max is, judged against eBay's minimum. */
sealed interface TypedMax {
    /** Nothing typed: the snipe only watches the price. */
    data object Empty : TypedMax

    /** Below what eBay accepts now. [typed] is the amount, or null for zero. */
    data class TooLow(
        val minimum: Money,
        val typed: Money?,
    ) : TypedMax

    /** A max Kestrel can bid: never changed by Kestrel, a hard ceiling. */
    data class Valid(
        val max: Money,
    ) : TypedMax
}

/** The odd-pence card (DESIGN S2 item 6): a suggestion for a round max, or a tip while no max is typed. */
@Immutable
sealed interface OddPence {
    /** "Make it £90.37" / "Odd pence beat bidders who stop at £90.00." */
    data class Suggestion(
        val amount: Money,
        val roundAmount: Money,
    ) : OddPence

    /** No max yet: the advice without an amount, in the listing's [currency] ("odd pence", "odd cents"). */
    data class Tip(
        val currency: String,
    ) : OddPence
}

/**
 * The rules of a max that S2 and the edit sheets share. Kestrel never changes the max the user types: the minimum
 * only refuses a max that is too low, the odd-pence card only suggests, and the amount check only asks.
 */
object MaxRules {
    /** The S2 and sheet lead times (DESIGN 3.17). */
    val LeadOptions: List<Int> = listOf(3, 6, 10)

    /** A max more than this many times the current price is spelt out before arming. */
    const val WORDS_CHECK_FACTOR = 3

    /** The pence the odd-pence card adds to a round max: £90.00 becomes £90.37. */
    private const val ODD_PENCE = 37L

    /**
     * eBay's lowest acceptable bid now, from the increment table ([BidIncrements]); for a UK private seller the
     * displayed price includes Buyer Protection. Null only when the table cannot price this currency.
     */
    fun minimumBid(
        site: EbaySite,
        current: Money,
        bidCount: Int,
        buyerProtectionIncluded: Boolean,
    ): Money? =
        try {
            BidIncrements.minimumNextBid(site, current, bidCount, buyerProtectionIncluded)
        } catch (error: RuntimeException) {
            null
        }

    /** Reads the max field's text ("90", "90.37") in [currency] against [minimum]. */
    fun read(
        text: String,
        currency: String,
        minimum: Money?,
    ): TypedMax {
        if (text.isBlank()) return TypedMax.Empty
        val max = MoneyText.parseUserInput(text, currency)
        val floor = minimum ?: Money(1, currency)
        return when {
            // A zero or unreadable amount is never a bid; with digits it is simply too low.
            max == null -> if (text.any { it.isDigit() }) TypedMax.TooLow(floor, typed = null) else TypedMax.Empty

            max.currency == floor.currency && max < floor -> TypedMax.TooLow(floor, typed = max)

            else -> TypedMax.Valid(max)
        }
    }

    /**
     * Why a max can't change once the bid window is open: the sheets say it the same way, whether the deadline passed
     * while the sheet was open or the data layer refused the save.
     */
    const val LOCKED_SENTENCE = "The bid window is open, so this can’t change now."

    /** "Too low: eBay needs at least £66.00". */
    fun tooLowMessage(minimum: Money): String = "Too low: eBay needs at least ${minimum.formatted()}"

    /**
     * The odd-pence card for what is typed in [currency]: a suggestion for a round valid max, a tip when empty, else
     * none.
     */
    fun oddPence(
        typed: TypedMax,
        currency: String,
    ): OddPence? =
        when (typed) {
            TypedMax.Empty -> {
                OddPence.Tip(currency)
            }

            is TypedMax.TooLow -> {
                null
            }

            is TypedMax.Valid -> {
                val max = typed.max
                if (max.minor % 100 ==
                    0L
                ) {
                    OddPence.Suggestion(Money(max.minor + ODD_PENCE, max.currency), max)
                } else {
                    null
                }
            }
        }

    /** The field text for an amount: "90.37". */
    fun fieldText(amount: Money): String = amount.major.toPlainString()

    /** True when [max] is more than [WORDS_CHECK_FACTOR] times [current], so the amount is spelt out first. */
    fun needsWordsCheck(
        max: Money,
        current: Money?,
    ): Boolean = current != null && current.currency == max.currency && max.minor > current.minor * WORDS_CHECK_FACTOR

    /** Max plus postage, when both are in the same currency. */
    fun worstCase(
        max: Money,
        postage: Money?,
    ): Money? = postage?.takeIf { it.currency == max.currency }?.let { max + it }

    /** "£", "US$", "€", "CHF": the symbol eBay's own pages put before an amount in [currency]. */
    fun currencySymbol(currency: String): String = MoneyText.format(Money.zero(currency)).removeSuffix("0.00").trim()
}
