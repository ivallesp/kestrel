package app.kestrel.ebay

import app.kestrel.model.Money
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.absoluteValue

/** Display and input of amounts. Implements [MoneyText]. */
internal object MoneyFormatting {
    /**
     * "£1,234.56", "US$69.99", "€7.50". Symbols are fixed rather than taken from the platform's currency data, so a
     * dollar is never a bare "$" and the JVM tests format exactly like the phone. [displayLocale] supplies only the
     * grouping and decimal separators.
     */
    fun format(
        money: Money,
        displayLocale: Locale,
    ): String {
        val symbols = DecimalFormatSymbols.getInstance(displayLocale)
        val absolute = if (money.minor == Long.MIN_VALUE) Long.MAX_VALUE else money.minor.absoluteValue
        val whole =
            (absolute / 100)
                .toString()
                .reversed()
                .chunked(3)
                .joinToString(symbols.groupingSeparator.toString())
                .reversed()
        val fraction = (absolute % 100).toString().padStart(2, '0')
        val sign = if (money.minor < 0) "-" else ""
        return "$sign${symbolFor(money.currency)}$whole${symbols.decimalSeparator}$fraction"
    }

    private fun symbolFor(currency: String): String =
        when (currency) {
            "GBP" -> "£"
            "USD" -> "US$"
            "EUR" -> "€"
            "CAD" -> "CA$"
            "AUD" -> "AU$"
            else -> "$currency "
        }

    /**
     * Parses a typed amount: an optional sign or code of [currency], thousands grouped in threes (comma, dot, space or
     * apostrophe), and a decimal point, or a decimal comma for currencies whose sites write one. The result becomes
     * a hard ceiling, so anything ambiguous, negative, zero or finer than a penny is null rather than a guess:
     * "1,234" is 1234 in pounds but could be a mistyped 12,34 in euros.
     */
    fun parseUserInput(
        text: String,
        currency: String,
    ): Money? {
        val withoutCurrency = stripCurrency(text.trim(), currency) ?: return null
        // Apostrophes and no-break spaces only ever group thousands, as in "1'234.50".
        val spaced = String(withoutCurrency.map { if (it in GROUPING_MARKS) ' ' else it }.toCharArray())
        val compact = spaced.replace(SPACE_RUN, " ").trim()
        if (compact.isEmpty() || compact.length > MAXIMUM_INPUT_LENGTH) return null
        if (!compact.all { it.isDigit() || it == '.' || it == ',' || it == ' ' }) return null
        val plain = plainDecimal(compact, decimalComma = currency in DECIMAL_COMMA_CURRENCIES) ?: return null
        val amount = plain.toBigDecimalOrNull() ?: return null
        if (amount.scale() > 2 || amount.signum() <= 0 || amount >= MAXIMUM_AMOUNT) return null
        return Money.of(amount, currency)
    }

    /** The text without currency markers; null when one names another currency. */
    private fun stripCurrency(
        text: String,
        currency: String,
    ): String? {
        var remaining = text
        for ((marker, markerCurrencies) in CURRENCY_MARKERS) {
            val index = remaining.indexOf(marker, ignoreCase = true)
            if (index < 0) continue
            if (currency !in markerCurrencies) return null
            remaining = remaining.removeRange(index, index + marker.length)
        }
        return remaining
    }

    /** "1,234.50" -> "1234.50"; null when the separators are malformed or could mean two different amounts. */
    private fun plainDecimal(
        compact: String,
        decimalComma: Boolean,
    ): String? {
        val dots = compact.count { it == '.' }
        val commas = compact.count { it == ',' }
        val decimalSeparator: Char? =
            when {
                dots > 0 && commas > 0 -> if (compact.lastIndexOf('.') > compact.lastIndexOf(',')) '.' else ','
                commas == 1 && decimalComma -> if (digitsAfterLast(compact, ',') <= 2) ',' else return null
                commas == 1 -> if (digitsAfterLast(compact, ',') == 3) null else return null
                dots == 1 -> if (digitsAfterLast(compact, '.') <= 2) '.' else return null
                dots > 1 && !decimalComma -> return null
                else -> null
            }
        if (decimalSeparator != null && compact.count { it == decimalSeparator } != 1) return null
        val integerPart = if (decimalSeparator == null) compact else compact.substringBeforeLast(decimalSeparator)
        val fraction = if (decimalSeparator == null) "" else compact.substringAfterLast(decimalSeparator)
        if (fraction.length > 2 || !fraction.all { it.isDigit() }) return null
        val integerDigits = joinedGroups(integerPart.trim()) ?: return null
        if (integerDigits.isEmpty() && fraction.isEmpty()) return null
        return "${integerDigits.ifEmpty { "0" }}.${fraction.ifEmpty { "0" }}"
    }

    /** "1,234 567" -> "1234567"; null unless every group after the first has exactly three digits. */
    private fun joinedGroups(integerPart: String): String? {
        val groups = integerPart.split(' ', ',', '.')
        if (groups.size == 1) return groups.single().takeIf { group -> group.all { it.isDigit() } }
        val valid =
            groups.first().length in 1..3 &&
                groups.drop(1).all { it.length == 3 } &&
                groups.all { group -> group.all { it.isDigit() } }
        return if (valid) groups.joinToString("") else null
    }

    private fun digitsAfterLast(
        text: String,
        separator: Char,
    ): Int = text.length - text.lastIndexOf(separator) - 1

    /** Longer markers first, so "US$" is removed before "$" is looked for. */
    private val CURRENCY_MARKERS =
        listOf(
            "US$" to setOf("USD"),
            "CA$" to setOf("CAD"),
            "AU$" to setOf("AUD"),
            "C$" to setOf("CAD"),
            "A$" to setOf("AUD"),
            "USD" to setOf("USD"),
            "CAD" to setOf("CAD"),
            "AUD" to setOf("AUD"),
            "GBP" to setOf("GBP"),
            "EUR" to setOf("EUR"),
            "CHF" to setOf("CHF"),
            "PLN" to setOf("PLN"),
            "zł" to setOf("PLN"),
            "£" to setOf("GBP"),
            "€" to setOf("EUR"),
            "$" to setOf("USD", "CAD", "AUD"),
        )

    // Sites that write 7,50: the euro sites and ebay.pl. ebay.ch writes 7.50 like the UK.
    private val DECIMAL_COMMA_CURRENCIES = setOf("EUR", "PLN")
    private const val GROUPING_MARKS = "'\u2019\u00A0\u202F\u2009"
    private val SPACE_RUN = Regex("\\s+")
    private const val MAXIMUM_INPUT_LENGTH = 24
    private val MAXIMUM_AMOUNT = BigDecimal("10000000000")
}
