package app.kestrel.ui.screens.add

import app.kestrel.model.Money

/** How a currency is named in words: its units and hundredths, one and many, and its name in a sentence. */
internal data class CurrencyWords(
    val one: String,
    val many: String,
    val minorOne: String,
    val minorMany: String,
    /** "US dollars", for "This listing is priced in US dollars". */
    val name: String,
) {
    companion object {
        fun of(currency: String): CurrencyWords =
            when (currency) {
                "GBP" -> CurrencyWords("pound", "pounds", "penny", "pence", "pounds")
                "EUR" -> CurrencyWords("euro", "euros", "cent", "cents", "euros")
                "USD" -> CurrencyWords("US dollar", "US dollars", "cent", "cents", "US dollars")
                "CAD" -> CurrencyWords("Canadian dollar", "Canadian dollars", "cent", "cents", "Canadian dollars")
                "AUD" -> CurrencyWords("Australian dollar", "Australian dollars", "cent", "cents", "Australian dollars")
                "CHF" -> CurrencyWords("Swiss franc", "Swiss francs", "centime", "centimes", "Swiss francs")
                "PLN" -> CurrencyWords("złoty", "złoty", "grosz", "groszy", "Polish złoty")
                else -> CurrencyWords(currency, currency, "hundredth", "hundredths", currency)
            }
    }
}

/**
 * An amount in British English words, for the check before a max far above the price: "nine hundred pounds",
 * "one thousand, two hundred and fifty pounds and thirty-seven pence". An extra digit is the costliest slip a
 * sniper can make, and words make it plain.
 */
internal object AmountInWords {
    /** "Nine hundred pounds": the amount in words with a capital, as the check shows it. */
    fun of(money: Money): String {
        val units = CurrencyWords.of(money.currency)
        val whole = money.minor / 100
        val hundredths = money.minor % 100
        val wholePart = if (whole > 0) "${number(whole)} ${if (whole == 1L) units.one else units.many}" else null
        val minorPart =
            if (hundredths > 0) {
                "${number(hundredths)} ${if (hundredths == 1L) units.minorOne else units.minorMany}"
            } else {
                null
            }
        val words =
            when {
                wholePart != null && minorPart != null -> "$wholePart and $minorPart"
                wholePart != null -> wholePart
                minorPart != null -> minorPart
                else -> "zero ${units.many}"
            }
        return words.replaceFirstChar { it.uppercase() }
    }

    /**
     * A whole number in words, British style: "one thousand and five", "two thousand, two hundred",
     * "one million, two hundred thousand". Negative numbers are not amounts and are refused.
     */
    fun number(value: Long): String {
        require(value >= 0) { "Amounts are never negative" }
        if (value == 0L) return "zero"
        val parts = mutableListOf<String>()
        var rest = value
        for ((size, name) in Scales) {
            if (rest >= size) {
                parts += "${belowThousand((rest / size).toInt())} $name"
                rest %= size
            }
        }
        if (rest > 0) {
            val last = belowThousand(rest.toInt())
            // A last group under a hundred is joined with "and": "one thousand and five".
            parts += if (parts.isNotEmpty() && rest < 100) "and $last" else last
        }
        return parts.reduce { joined, part -> if (part.startsWith("and ")) "$joined $part" else "$joined, $part" }
    }

    private fun belowThousand(value: Int): String {
        val hundreds = value / 100
        val rest = value % 100
        return when {
            hundreds == 0 -> belowHundred(rest)
            rest == 0 -> "${Ones[hundreds]} hundred"
            else -> "${Ones[hundreds]} hundred and ${belowHundred(rest)}"
        }
    }

    private fun belowHundred(value: Int): String =
        when {
            value < 20 -> Ones[value]
            value % 10 == 0 -> Tens[value / 10]
            else -> "${Tens[value / 10]}-${Ones[value % 10]}"
        }

    private val Scales = listOf(1_000_000_000L to "billion", 1_000_000L to "million", 1_000L to "thousand")

    private val Ones =
        listOf(
            "zero",
            "one",
            "two",
            "three",
            "four",
            "five",
            "six",
            "seven",
            "eight",
            "nine",
            "ten",
            "eleven",
            "twelve",
            "thirteen",
            "fourteen",
            "fifteen",
            "sixteen",
            "seventeen",
            "eighteen",
            "nineteen",
        )

    private val Tens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
}
