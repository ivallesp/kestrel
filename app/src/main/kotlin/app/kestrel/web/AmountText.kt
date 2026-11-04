package app.kestrel.web

import app.kestrel.model.EbaySite
import app.kestrel.model.Money
import java.math.BigDecimal

/**
 * The text Kestrel types into eBay's bid field, and amounts read back from the field or from the bid layer.
 * Typing never uses a grouping separator, and a whole amount is typed without decimals, so a field that reads
 * "1.500" as one and a half or as fifteen hundred cannot misread it. Reading refuses anything ambiguous: an
 * unreadable amount stops the bid, a misread one could exceed the user's maximum.
 */
object AmountText {
    private const val DOLLAR = "$"

    private val numberPattern = Regex("""\d[\d.,'\s\u00a0\u202f]*\d|\d""")

    // Every kind of space counts as one grouping character, like the apostrophe ("1'234.50").
    private val spaces = Regex("""[\s\u00a0\u202f]""")
    private val dollarCurrencies = setOf("USD", "CAD", "AUD")

    // Checked in order: the specific dollar prefixes before the bare dollar sign.
    private val currencyMarkers: List<Pair<Regex, String>> =
        listOf(
            """US\s?\$|\bUSD\b""" to "USD",
            """\bC\s?\$|\bCA\s?\$|\bCAD\b""" to "CAD",
            """\bAU\s?\$|\bA\s?\$|\bAUD\b""" to "AUD",
            """\u00a3|\bGBP\b""" to "GBP",
            """\u20ac|\bEUR\b""" to "EUR",
            """\bCHF\b""" to "CHF",
            """z\u0142|\bPLN\b""" to "PLN",
            """\$""" to DOLLAR,
        ).map { (pattern, code) -> Regex(pattern) to code }

    // What may surround the number in the bid field: nothing, or one currency marker.
    private val fieldMarker =
        Regex("""^(?:|\u00a3|\u20ac|\$|US\s?\$|C\s?\$|AU\s?\$|GBP|USD|EUR|CHF|CAD|AUD|PLN|z\u0142)$""")

    /** The site's decimal separator as its users type it. */
    fun decimalSeparator(site: EbaySite): Char =
        when (site) {
            EbaySite.UK, EbaySite.US, EbaySite.IE, EbaySite.CA, EbaySite.AU, EbaySite.CH -> '.'
            EbaySite.DE, EbaySite.AT, EbaySite.FR, EbaySite.IT, EbaySite.ES, EbaySite.NL, EbaySite.PL -> ','
        }

    /** The text to type: "90", or "90.37" / "90,37" by site; a type=number field only accepts a full stop. */
    fun forInput(
        amount: Money,
        site: EbaySite,
        inputType: String?,
    ): String {
        require(amount.minor > 0) { "A maximum bid must be above zero" }
        val whole = amount.minor / 100
        val cents = amount.minor % 100
        if (cents == 0L) return whole.toString()
        val separator = if (inputType.equals("number", ignoreCase = true)) '.' else decimalSeparator(site)
        return "$whole$separator${cents.toString().padStart(2, '0')}"
    }

    /** The amount the bid field shows, or null when it is not one plain amount in [currency]. */
    fun readField(
        fieldValue: String,
        site: EbaySite,
        currency: String,
    ): Money? {
        val match = numberPattern.find(fieldValue) ?: return null
        val before = fieldValue.substring(0, match.range.first).trim(' ', '\u00a0', '\u202f', '\t')
        val after = fieldValue.substring(match.range.last + 1).trim(' ', '\u00a0', '\u202f', '\t')
        val marker = (before + after).trim()
        if (before.isNotEmpty() && after.isNotEmpty()) return null
        if (!fieldMarker.matches(marker)) return null
        if (marker.isNotEmpty() && !markerFits(currencyNamedIn(marker), currency)) return null
        return parseNumber(match.value, decimalSeparator(site))?.let { Money.of(it, currency) }
    }

    /**
     * An amount as eBay displays it (a pound sign and 5.57, "US $1,234.50", "EUR 7,50"), in [currency]. Null when the
     * text has no amount, names another currency, or could be read two ways.
     */
    fun parseDisplayed(
        text: String,
        currency: String,
    ): Money? {
        if (!markerFits(currencyNamedIn(text), currency)) return null
        val number = numberPattern.find(text)?.value ?: return null
        return parseNumber(number, null)?.let { Money.of(it, currency) }
    }

    private fun markerFits(
        named: String?,
        currency: String,
    ): Boolean = named == null || named == currency || (named == DOLLAR && currency in dollarCurrencies)

    private fun currencyNamedIn(text: String): String? =
        currencyMarkers.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second

    /**
     * Reads digits with separators. The last full stop or comma is the decimal separator when one or two digits
     * follow it. Every other separator (full stop, comma, space, apostrophe) must be one kind of grouping in groups of
     * three. Three digits after a single full stop or comma are grouping, unless that is the site's decimal
     * separator, in which case "1.500" could mean either and is refused.
     */
    private fun parseNumber(
        raw: String,
        siteDecimalSeparator: Char?,
    ): BigDecimal? {
        val text = raw.replace(spaces, " ")
        val lastSeparatorIndex = text.indexOfLast { it == '.' || it == ',' }
        val digitsAfterLast = text.length - lastSeparatorIndex - 1
        val decimalIndex = lastSeparatorIndex.takeIf { it >= 0 && digitsAfterLast in 1..2 }
        if (decimalIndex == null && lastSeparatorIndex >= 0) {
            if (digitsAfterLast != 3 || text[lastSeparatorIndex] == siteDecimalSeparator) return null
        }
        val integerPart = if (decimalIndex == null) text else text.substring(0, decimalIndex)
        val fraction = if (decimalIndex == null) "" else text.substring(decimalIndex + 1)
        val grouping = integerPart.filterNot(Char::isDigit).toSet()
        if (grouping.size > 1) return null
        if (decimalIndex != null && text[decimalIndex] in grouping) return null
        if (grouping.isNotEmpty() && !validGroups(integerPart.split(grouping.single()))) return null
        val digits = integerPart.filter(Char::isDigit)
        if (digits.isEmpty() || !fraction.all(Char::isDigit)) return null
        return (if (fraction.isEmpty()) digits else "$digits.$fraction").toBigDecimalOrNull()
    }

    private fun validGroups(groups: List<String>): Boolean =
        groups.first().length in 1..3 &&
            groups.first().all(Char::isDigit) &&
            groups.drop(1).all { group -> group.length == 3 && group.all(Char::isDigit) }
}
