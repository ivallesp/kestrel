package app.kestrel.ebay

import app.kestrel.model.Money
import java.math.BigDecimal

/**
 * Reads amounts from eBay's display texts ("£0.94", "US $10.00", "EUR 7,50", "GBP 8.51"). Only used where the
 * model has no numeric value (the Buyer Protection line, the postage summary). eBay prints at most two decimals, so
 * a last separator followed by one or two digits is the decimal point and every other separator groups thousands,
 * whatever the site's convention.
 */
internal object DisplayMoney {
    data class Amount(
        /** ISO code, or null for a bare "$" whose dollar cannot be told. */
        val currency: String?,
        val value: BigDecimal,
    )

    /** The first amount with a currency marker in [text]. */
    fun first(text: String): Amount? {
        for (match in AMOUNT.findAll(text)) {
            val prefix = match.groups[1]?.value
            val suffix = match.groups[3]?.value
            val marker = prefix ?: suffix ?: continue
            val value = parseNumber(match.groupValues[2]) ?: continue
            return Amount(currencyOf(marker), value)
        }
        return null
    }

    /** The first amount in [text] as Money in [expectedCurrency]; null when the text names a different currency. */
    fun firstIn(
        text: String,
        expectedCurrency: String,
    ): Money? {
        val amount = first(text) ?: return null
        val currency = amount.currency ?: expectedCurrency.takeIf { it in DOLLAR_CURRENCIES } ?: return null
        if (currency != expectedCurrency) return null
        return moneyOrNull(amount.value, currency)
    }

    private fun parseNumber(raw: String): BigDecimal? {
        val compact = raw.filterNot { it in GROUPING_SPACES || it == '\'' }
        val lastSeparator = compact.indexOfLast { it == '.' || it == ',' }
        val digitsAfter = if (lastSeparator < 0) 0 else compact.length - lastSeparator - 1
        val normalized =
            if (lastSeparator >= 0 && digitsAfter in 1..2) {
                compact.substring(0, lastSeparator).filter { it.isDigit() } + "." + compact.substring(lastSeparator + 1)
            } else {
                compact.filter { it.isDigit() }
            }
        if (normalized.isEmpty() || normalized.startsWith(".")) return null
        return try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun currencyOf(marker: String): String? =
        when (marker.filterNot { it.isWhitespace() || it in GROUPING_SPACES }.uppercase()) {
            "US$", "USD" -> "USD"
            "C$", "CA$", "CAD" -> "CAD"
            "AU$", "A$", "AUD" -> "AUD"
            "£", "GBP" -> "GBP"
            "€", "EUR" -> "EUR"
            "CHF" -> "CHF"
            "PLN", "ZŁ" -> "PLN"
            else -> null
        }

    private val DOLLAR_CURRENCIES = setOf("USD", "CAD", "AUD")
    private const val GROUPING_SPACES = "\u00A0\u202F\u2009"

    // Group 1: prefix marker, group 2: number, group 3: suffix marker. Plain spaces are not accepted inside a
    // number, so "£2.00 2 items" never reads as 2002.
    private val AMOUNT =
        Regex(
            "(US\\s?\\$|CA?\\s?\\$|AU?\\s?\\$|\\$|£|€|GBP|EUR|USD|CAD|AUD|CHF|PLN)?[\\s\\u00A0]?" +
                "(\\d(?:[\\d.,'\\u00A0\\u202F\\u2009]*\\d)?)" +
                "(?:[\\s\\u00A0]?(€|zł|EUR|GBP|USD|CHF|PLN))?",
        )
}
