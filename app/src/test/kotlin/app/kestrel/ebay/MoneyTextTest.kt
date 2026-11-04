package app.kestrel.ebay

import app.kestrel.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

class MoneyTextTest {
    @Test
    fun formatsUkStyleWithUnambiguousForeignSymbols() {
        val cases =
            listOf(
                Money(123456, "GBP") to "£1,234.56",
                Money(6999, "USD") to "US$69.99",
                Money(750, "EUR") to "€7.50",
                Money(0, "GBP") to "£0.00",
                Money(5, "GBP") to "£0.05",
                Money(100000000, "GBP") to "£1,000,000.00",
                Money(-500, "GBP") to "-£5.00",
                Money(1000, "CAD") to "CA$10.00",
                Money(1000, "AUD") to "AU$10.00",
                Money(1050, "CHF") to "CHF 10.50",
                Money(2500, "PLN") to "PLN 25.00",
            )
        for ((money, text) in cases) assertEquals(text, MoneyText.format(money))
    }

    @Test
    fun separatorsFollowTheDisplayLocale() {
        assertEquals("€1.234,50", MoneyText.format(Money(123450, "EUR"), Locale.GERMANY))
        assertEquals("£1,234.50", MoneyText.format(Money(123450, "GBP"), Locale.US))
    }

    @Test
    fun parsesTypicalInput() {
        val cases =
            listOf(
                Triple("90", "GBP", "90.00"),
                Triple("90.37", "GBP", "90.37"),
                Triple("£90.37", "GBP", "90.37"),
                Triple("£ 90.37", "GBP", "90.37"),
                Triple("90.37 GBP", "GBP", "90.37"),
                Triple("1,234.50", "GBP", "1234.50"),
                Triple("1,234", "GBP", "1234.00"),
                Triple("1,234,567.89", "GBP", "1234567.89"),
                Triple("  90.5 ", "GBP", "90.50"),
                Triple(".50", "GBP", "0.50"),
                Triple("90.", "GBP", "90.00"),
                Triple("US$69.99", "USD", "69.99"),
                Triple("$69.99", "USD", "69.99"),
                Triple("69.99 USD", "USD", "69.99"),
                Triple("CA$12", "CAD", "12.00"),
                Triple("$12", "AUD", "12.00"),
                Triple("90,37", "EUR", "90.37"),
                Triple("90.37", "EUR", "90.37"),
                Triple("€90,37", "EUR", "90.37"),
                Triple("90,37 €", "EUR", "90.37"),
                Triple("EUR 7,5", "EUR", "7.50"),
                Triple("1.234,50", "EUR", "1234.50"),
                Triple("1 234,50", "EUR", "1234.50"),
                Triple("1\u00A0234,50", "EUR", "1234.50"),
                Triple("1\u202F234,50", "EUR", "1234.50"),
                Triple("1.234.567", "EUR", "1234567.00"),
                Triple("1'234.50", "CHF", "1234.50"),
                Triple("90,50 zł", "PLN", "90.50"),
            )
        for ((input, currency, amount) in cases) {
            assertEquals("input: $input", Money.of(amount, currency), MoneyText.parseUserInput(input, currency))
        }
    }

    @Test
    fun rejectsAmbiguousOrInvalidInput() {
        val cases =
            listOf(
                "" to "GBP",
                "   " to "GBP",
                "abc" to "GBP",
                "ninety" to "GBP",
                "0" to "GBP",
                "0.00" to "GBP",
                "-5" to "GBP",
                "90.375" to "GBP",
                "90,37" to "GBP",
                "1.234" to "GBP",
                "1.234.567" to "GBP",
                "1,2,3" to "GBP",
                "12 34" to "GBP",
                "1.234,567" to "EUR",
                "1,234" to "EUR",
                "1.234" to "EUR",
                "£90" to "USD",
                "$90" to "GBP",
                "€90" to "GBP",
                "90 EUR" to "GBP",
                "US$90" to "CAD",
                "1e5" to "GBP",
                "12345678901234567890" to "GBP",
                "90..5" to "GBP",
                "9,0.5" to "GBP",
            )
        for ((input, currency) in cases) {
            assertEquals(
                "input: $input ($currency)",
                null,
                MoneyText.parseUserInput(input, currency),
            )
        }
    }

    @Test
    fun arbitraryInputNeverThrows() {
        val random = Random(11)
        val characters = "0123456789.,' \u00A3\$\u20AC-+eEUSDGBPz\u0142\u00A0\u202F\u2019"
        repeat(20_000) {
            val text =
                buildString { repeat(random.nextInt(0, 16)) { append(characters[random.nextInt(characters.length)]) } }
            for (currency in listOf("GBP", "EUR", "USD")) {
                val parsed = MoneyFormatting.parseUserInput(text, currency)
                if (parsed != null) assertEquals(text, currency, parsed.currency)
            }
        }
    }

    @Test
    fun formatAndParseRoundTrip() {
        for (minor in listOf(1L, 99L, 100L, 12345L, 99999L, 100000L, 123456789L)) {
            for (currency in listOf("GBP", "USD", "EUR", "CAD", "AUD", "CHF", "PLN")) {
                val money = Money(minor, currency)
                assertEquals("$money", money, MoneyText.parseUserInput(MoneyText.format(money), currency))
            }
        }
    }
}
