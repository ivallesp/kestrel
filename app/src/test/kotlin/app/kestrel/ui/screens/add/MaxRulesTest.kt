package app.kestrel.ui.screens.add

import app.kestrel.model.Money
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.CatalogueFixture.gbp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The max is a hard ceiling: read exactly, judged against eBay's minimum, and spelt out when it is far above the price. */
class MaxRulesTest {
    @Test
    fun theMaxIsReadExactlyAsTyped() {
        val minimum = gbp("66.08")
        assertEquals(TypedMax.Valid(gbp("90.37")), MaxRules.read("90.37", "GBP", minimum))
        assertEquals(TypedMax.Valid(gbp("90.00")), MaxRules.read("90", "GBP", minimum))
        assertEquals(TypedMax.Valid(gbp("66.08")), MaxRules.read("66.08", "GBP", minimum))
        assertEquals(TypedMax.Empty, MaxRules.read("", "GBP", minimum))
        assertEquals(TypedMax.Empty, MaxRules.read(".", "GBP", minimum))
    }

    @Test
    fun belowTheMinimumOrZeroIsTooLow() {
        val minimum = gbp("66.08")
        assertEquals(TypedMax.TooLow(minimum, gbp("66.07")), MaxRules.read("66.07", "GBP", minimum))
        assertEquals(TypedMax.TooLow(minimum, typed = null), MaxRules.read("0.00", "GBP", minimum))
        assertEquals("Too low: eBay needs at least £66.08", MaxRules.tooLowMessage(minimum))
    }

    @Test
    fun theMinimumComesFromTheIncrementTable() {
        // DESIGN MF32: the Omega's next bid is £2,150.00 plus the £50.00 step.
        val omega = CatalogueFixture.omega
        assertEquals(
            gbp("2200.00"),
            MaxRules.minimumBid(omega.ref.site, omega.lastPrice!!, omega.lastBidCount!!, false),
        )
        val polaroid = CatalogueFixture.polaroid
        assertEquals(
            gbp("63.00"),
            MaxRules.minimumBid(polaroid.ref.site, polaroid.lastPrice!!, polaroid.lastBidCount!!, false),
        )
        // With no bids the starting price itself is a valid bid.
        assertEquals(gbp("64.00"), MaxRules.minimumBid(omega.ref.site, gbp("64.00"), 0, false))
    }

    @Test
    fun oddPenceOnlySuggestsForARoundValidMax() {
        assertEquals(
            OddPence.Suggestion(gbp("90.37"), gbp("90.00")),
            MaxRules.oddPence(TypedMax.Valid(gbp("90.00")), "GBP"),
        )
        assertNull(MaxRules.oddPence(TypedMax.Valid(gbp("90.37")), "GBP"))
        assertNull(MaxRules.oddPence(TypedMax.TooLow(gbp("66.08"), gbp("60.00")), "GBP"))
        assertEquals(OddPence.Tip("GBP"), MaxRules.oddPence(TypedMax.Empty, "GBP"))
    }

    @Test
    fun aMaxMoreThanThreeTimesThePriceIsSpeltOut() {
        assertTrue(MaxRules.needsWordsCheck(gbp("192.01"), gbp("64.00")))
        assertFalse(MaxRules.needsWordsCheck(gbp("192.00"), gbp("64.00")))
        assertFalse(MaxRules.needsWordsCheck(gbp("900.00"), null))
        assertFalse(MaxRules.needsWordsCheck(Money.of("900.00", "USD"), gbp("64.00")))
    }

    @Test
    fun currencySymbolsAreThoseOfEbaysPages() {
        assertEquals("£", MaxRules.currencySymbol("GBP"))
        assertEquals("US$", MaxRules.currencySymbol("USD"))
        assertEquals("€", MaxRules.currencySymbol("EUR"))
        assertEquals("CHF", MaxRules.currencySymbol("CHF"))
    }

    @Test
    fun amountsReadInBritishEnglishWords() {
        assertEquals("Nine hundred pounds", AmountInWords.of(gbp("900.00")))
        assertEquals("Ninety pounds and thirty-seven pence", AmountInWords.of(gbp("90.37")))
        assertEquals("One pound and one penny", AmountInWords.of(gbp("1.01")))
        assertEquals("Fifty pence", AmountInWords.of(gbp("0.50")))
        assertEquals(
            "One thousand, two hundred and fifty pounds",
            AmountInWords.of(gbp("1250.00")),
        )
        assertEquals("Two thousand and five pounds", AmountInWords.of(gbp("2005.00")))
        assertEquals("Nine hundred US dollars", AmountInWords.of(Money.of("900.00", "USD")))
        assertEquals("one million, two hundred thousand", AmountInWords.number(1_200_000))
        assertEquals("one hundred and twelve thousand, three hundred and forty-five", AmountInWords.number(112_345))
        assertEquals("zero", AmountInWords.number(0))
    }
}
