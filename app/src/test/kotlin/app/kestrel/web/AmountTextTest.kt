package app.kestrel.web

import app.kestrel.model.EbaySite
import app.kestrel.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AmountTextTest {
    private fun gbp(major: String) = Money.of(major, "GBP")

    private fun eur(major: String) = Money.of(major, "EUR")

    @Test
    fun `whole amounts are typed without decimals or grouping`() {
        assertEquals("90", AmountText.forInput(gbp("90"), EbaySite.UK, "tel"))
        assertEquals("1500", AmountText.forInput(gbp("1500"), EbaySite.UK, "tel"))
        assertEquals("1500", AmountText.forInput(eur("1500"), EbaySite.DE, "tel"))
    }

    @Test
    fun `amounts with pence use the site's decimal separator`() {
        assertEquals("90.37", AmountText.forInput(gbp("90.37"), EbaySite.UK, "tel"))
        assertEquals("90,37", AmountText.forInput(eur("90.37"), EbaySite.DE, "tel"))
        assertEquals("1234,05", AmountText.forInput(eur("1234.05"), EbaySite.FR, null))
        assertEquals("12.50", AmountText.forInput(Money.of("12.5", "CHF"), EbaySite.CH, "text"))
        assertEquals("0.05", AmountText.forInput(gbp("0.05"), EbaySite.UK, "tel"))
    }

    @Test
    fun `a number field only takes a full stop`() {
        assertEquals("90.37", AmountText.forInput(eur("90.37"), EbaySite.DE, "number"))
    }

    @Test
    fun `zero and negative maximums are refused`() {
        assertThrows(IllegalArgumentException::class.java) { AmountText.forInput(gbp("0"), EbaySite.UK, "tel") }
        assertThrows(
            IllegalArgumentException::class.java,
        ) { AmountText.forInput(Money(-100, "GBP"), EbaySite.UK, "tel") }
    }

    @Test
    fun `the field reads back what was typed`() {
        assertEquals(gbp("90.37"), AmountText.readField("90.37", EbaySite.UK, "GBP"))
        assertEquals(gbp("90"), AmountText.readField("90", EbaySite.UK, "GBP"))
        assertEquals(eur("90.37"), AmountText.readField("90,37", EbaySite.DE, "EUR"))
        assertEquals(gbp("12.5"), AmountText.readField("12.5", EbaySite.UK, "GBP"))
    }

    @Test
    fun `the field may be reformatted by eBay`() {
        assertEquals(gbp("1500"), AmountText.readField("1,500.00", EbaySite.UK, "GBP"))
        assertEquals(gbp("1500"), AmountText.readField("1,500", EbaySite.UK, "GBP"))
        assertEquals(eur("1500"), AmountText.readField("1.500,00", EbaySite.DE, "EUR"))
        assertEquals(eur("1500"), AmountText.readField("1.500", EbaySite.DE, "EUR"))
        assertEquals(gbp("90.37"), AmountText.readField("£90.37", EbaySite.UK, "GBP"))
        assertEquals(gbp("90.37"), AmountText.readField(" 90.37 ", EbaySite.UK, "GBP"))
    }

    @Test
    fun `an ambiguous or foreign field value is refused`() {
        // "1.500" on ebay.co.uk could be one and a half or fifteen hundred.
        assertNull(AmountText.readField("1.500", EbaySite.UK, "GBP"))
        assertNull(AmountText.readField("1,500", EbaySite.DE, "EUR"))
        assertNull(AmountText.readField("", EbaySite.UK, "GBP"))
        assertNull(AmountText.readField("abc", EbaySite.UK, "GBP"))
        assertNull(AmountText.readField("12.345", EbaySite.UK, "GBP"))
        assertNull(AmountText.readField("12abc", EbaySite.UK, "GBP"))
        assertNull(AmountText.readField("1.2.3", EbaySite.UK, "GBP"))
        assertNull(AmountText.readField("US $90.37", EbaySite.UK, "GBP"))
        assertNull(AmountText.readField("12 34", EbaySite.UK, "GBP"))
        assertNull(AmountText.readField("1,234.567,50", EbaySite.UK, "GBP"))
        assertNull(AmountText.readField("1 234.5'00", EbaySite.UK, "GBP"))
    }

    @Test
    fun `space and apostrophe grouping is read in groups of three`() {
        assertEquals(eur("1234.5"), AmountText.readField("1 234,50", EbaySite.FR, "EUR"))
        assertEquals(eur("1234.5"), AmountText.readField("1\u00a0234,50", EbaySite.FR, "EUR"))
        assertEquals(Money.of("1234.5", "CHF"), AmountText.readField("1'234.50", EbaySite.CH, "CHF"))
    }

    @Test
    fun `displayed amounts are read in the bid currency`() {
        assertEquals(gbp("5.57"), AmountText.parseDisplayed("£5.57", "GBP"))
        assertEquals(Money.of("1234.50", "USD"), AmountText.parseDisplayed("US $1,234.50", "USD"))
        assertEquals(Money.of("3.25", "USD"), AmountText.parseDisplayed("$3.25", "USD"))
        assertEquals(eur("7.50"), AmountText.parseDisplayed("EUR 7,50", "EUR"))
        assertEquals(eur("1234.50"), AmountText.parseDisplayed("1.234,50 €", "EUR"))
        assertEquals(Money.of("1234.50", "CHF"), AmountText.parseDisplayed("CHF 1'234.50", "CHF"))
        assertEquals(gbp("1234"), AmountText.parseDisplayed("£1,234", "GBP"))
        assertEquals(Money.of("5", "CAD"), AmountText.parseDisplayed("C $5.00", "CAD"))
        assertEquals(Money.of("19.99", "PLN"), AmountText.parseDisplayed("19,99 zł", "PLN"))
    }

    @Test
    fun `a displayed amount in another currency is not read`() {
        assertNull(AmountText.parseDisplayed("US $24.99", "GBP"))
        assertNull(AmountText.parseDisplayed("£18.76", "USD"))
        assertNull(AmountText.parseDisplayed("C $5.00", "USD"))
        assertNull(AmountText.parseDisplayed("EUR 7,50", "GBP"))
        assertNull(AmountText.parseDisplayed("$5.00", "GBP"))
        assertNull(AmountText.parseDisplayed("no amount here", "GBP"))
    }
}
