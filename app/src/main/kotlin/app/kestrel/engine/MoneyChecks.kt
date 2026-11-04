package app.kestrel.engine

import app.kestrel.model.Money

/** Compares two amounts only when they are in the same currency; null otherwise, so pounds are never compared with dollars. */
internal fun Money.compareSafely(other: Money): Int? {
    if (currency != other.currency) return null
    return minor.compareTo(other.minor)
}

internal fun Money.isAbove(other: Money): Boolean? = compareSafely(other)?.let { it > 0 }

/** The higher of two amounts; the first one when they cannot be compared. */
internal fun higherOf(
    first: Money?,
    second: Money?,
): Money? {
    if (first == null) return second
    if (second == null) return first
    val comparison = first.compareSafely(second) ?: return first
    return if (comparison >= 0) first else second
}

/** "£48.00", "€7.50", "US$69.99": short amounts for timeline messages. */
internal fun Money.displayText(): String {
    val prefix =
        when (currency) {
            "GBP" -> "£"
            "EUR" -> "€"
            "USD" -> "US$"
            "CAD" -> "C$"
            "AUD" -> "AU$"
            else -> "$currency "
        }
    return prefix + major.toPlainString()
}

/**
 * Reads back the amount eBay's bid field shows. The max is a hard ceiling, so the field must provably hold exactly the
 * max before a bid: anything ambiguous reads as null and stops the bid.
 */
internal object AmountField {
    // Digits, separators and the spaces or apostrophes some locales group thousands with (no-break spaces included).
    private val numberBody = Regex("[0-9.,\\s\\u00A0\\u202F']+")
    private const val MAXIMUM_WHOLE_DIGITS = 12

    /** Minor units (pence, cents) of [fieldValue], ignoring a currency symbol or code around the number. */
    fun minorUnits(fieldValue: String): Long? {
        val firstDigit = fieldValue.indexOfFirst { it in '0'..'9' }
        val lastDigit = fieldValue.indexOfLast { it in '0'..'9' }
        if (firstDigit < 0) return null
        val body = fieldValue.substring(firstDigit, lastDigit + 1)
        if (!numberBody.matches(body)) return null
        val number = body.filter { it in '0'..'9' || it == '.' || it == ',' }
        val lastSeparator = number.indexOfLast { it == '.' || it == ',' }
        val digitsAfterLast = number.length - lastSeparator - 1
        val hasDecimals = lastSeparator >= 0 && digitsAfterLast in 1..2
        val wholePart = if (hasDecimals) number.substring(0, lastSeparator) else number
        val fractionPart = if (hasDecimals) number.substring(lastSeparator + 1) else ""
        val decimalSeparator = if (hasDecimals) number[lastSeparator] else null
        if (!isWholeNumber(wholePart, decimalSeparator)) return null
        val wholeDigits = wholePart.filter { it in '0'..'9' }
        if (wholeDigits.isEmpty() || wholeDigits.length > MAXIMUM_WHOLE_DIGITS) return null
        return wholeDigits.toLong() * 100 + fractionPart.padEnd(2, '0').toLong()
    }

    // Digits, optionally grouped in threes by one separator that differs from the decimal separator.
    private fun isWholeNumber(
        wholePart: String,
        decimalSeparator: Char?,
    ): Boolean {
        val separators = wholePart.filter { it == '.' || it == ',' }.toSet()
        if (separators.isEmpty()) return true
        if (separators.size > 1 || separators.first() == decimalSeparator) return false
        val groups = wholePart.split(separators.first())
        return groups.first().length in 1..3 && groups.drop(1).all { it.length == 3 }
    }
}
