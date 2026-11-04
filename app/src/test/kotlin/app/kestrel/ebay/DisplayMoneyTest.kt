package app.kestrel.ebay

import app.kestrel.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Amounts as eBay prints them on ebay.co.uk, ebay.com and ebay.de (bidflow AR4, UR7). */
class DisplayMoneyTest {
    @Test
    fun readsEbaysDisplayFormats() {
        val cases =
            listOf(
                Triple("£5.37", "GBP", "5.37"),
                Triple("incl. £0.94 for ", "GBP", "0.94"),
                Triple("US \$69.99", "USD", "69.99"),
                Triple("US \$10.00 ", "USD", "10.00"),
                Triple("US \$1,234.56", "USD", "1234.56"),
                Triple("EUR 7,50", "EUR", "7.50"),
                Triple("EUR 1.234,56", "EUR", "1234.56"),
                Triple("EUR 47.10", "EUR", "47.10"),
                Triple("GBP 8.51", "GBP", "8.51"),
                Triple("7,50 €", "EUR", "7.50"),
                Triple("£1,500", "GBP", "1500.00"),
                Triple("AU \$12.00", "AUD", "12.00"),
                Triple("C \$5.00", "CAD", "5.00"),
                Triple("CHF 1'234.50", "CHF", "1234.50"),
                Triple("25,00 zł", "PLN", "25.00"),
                Triple("\$3.25", "USD", "3.25"),
            )
        for ((text, currency, amount) in cases) {
            assertEquals(
                text,
                Money.of(amount, currency),
                DisplayMoney.firstIn(text, currency),
            )
        }
    }

    @Test
    fun takesTheFirstMarkedAmountOnly() {
        assertEquals(usd("10.00"), DisplayMoney.firstIn("US \$10.00 (approx £7.43)", "USD"))
        assertEquals(gbp("2.00"), DisplayMoney.firstIn("£2.00 2 items", "GBP"))
        assertEquals(gbp("6.00"), DisplayMoney.firstIn("1 bid, £6.00", "GBP"))
    }

    @Test
    fun refusesOtherCurrenciesAndUnmarkedNumbers() {
        assertNull(DisplayMoney.firstIn("US \$10.00", "GBP"))
        assertNull(DisplayMoney.firstIn("\$10.00", "GBP"))
        assertNull(DisplayMoney.firstIn("Will post to United States. Read item description or ", "GBP"))
        assertNull(DisplayMoney.firstIn("2 Min 28 Sek", "EUR"))
        assertNull(DisplayMoney.firstIn("", "GBP"))
    }
}
