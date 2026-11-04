package app.kestrel.ebay

import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** rules.md BUY4: £0.10 + 7% to £20 + 4% from £20 to £300 + 2% from £300 to £4,000. */
class BuyerProtectionFeeTest {
    @Test
    fun ebaysPublishedExamples() {
        // eBay UK help id=5594 (Wayback 2026-10-25).
        assertEquals(gbp("0.45"), BuyerProtectionFee.feeFor(gbp("5.00")))
        assertEquals(gbp("1.50"), BuyerProtectionFee.feeFor(gbp("20.00")))
        assertEquals(gbp("36.70"), BuyerProtectionFee.feeFor(gbp("1500.00")))
        assertEquals(gbp("86.70"), BuyerProtectionFee.feeFor(gbp("7500.00")))
        // Gixen forum t=11857: bid history £191.83, item page "incl. £8.37", displayed £200.20.
        assertEquals(gbp("8.37"), BuyerProtectionFee.feeFor(gbp("191.83")))
        // 2026-09 UK fixtures quoted in bidflow.verify MF3: £50 auction -> £2.70, £90 Buy It Now -> £4.30.
        assertEquals(gbp("2.70"), BuyerProtectionFee.feeFor(gbp("50.00")))
        assertEquals(gbp("4.30"), BuyerProtectionFee.feeFor(gbp("90.00")))
    }

    @Test
    fun bandEdgesAndCap() {
        assertEquals(gbp("0.10"), BuyerProtectionFee.feeFor(gbp("0.01")))
        assertEquals(gbp("1.50"), BuyerProtectionFee.feeFor(gbp("20.00")))
        assertEquals(gbp("12.70"), BuyerProtectionFee.feeFor(gbp("300.00")))
        assertEquals(gbp("86.70"), BuyerProtectionFee.feeFor(gbp("4000.00")))
        assertEquals(gbp("86.70"), BuyerProtectionFee.feeFor(gbp("1000000.00")))
        assertEquals(gbp("0.00"), BuyerProtectionFee.feeFor(gbp("0.00")))
        // Rounded half up to the penny: £0.10 + 7% of £0.07 is £0.1049, of £0.08 £0.1056.
        assertEquals(gbp("0.10"), BuyerProtectionFee.feeFor(gbp("0.07")))
        assertEquals(gbp("0.11"), BuyerProtectionFee.feeFor(gbp("0.08")))
    }

    @Test
    fun inverseOfTheExamples() {
        assertEquals(gbp("191.83"), BuyerProtectionFee.itemPriceFromDisplayed(gbp("200.20")))
        assertEquals(gbp("5.00"), BuyerProtectionFee.itemPriceFromDisplayed(gbp("5.45")))
        assertEquals(gbp("20.00"), BuyerProtectionFee.itemPriceFromDisplayed(gbp("21.50")))
        assertEquals(gbp("1500.00"), BuyerProtectionFee.itemPriceFromDisplayed(gbp("1536.70")))
        assertEquals(gbp("7500.00"), BuyerProtectionFee.itemPriceFromDisplayed(gbp("7586.70")))
        assertEquals(gbp("0.00"), BuyerProtectionFee.itemPriceFromDisplayed(gbp("0.10")))
        assertEquals(gbp("0.01"), BuyerProtectionFee.itemPriceFromDisplayed(gbp("0.11")))
    }

    @Test
    fun roundTripsEveryPennyFromOnePennyToFiveThousandPounds() {
        var previousFee = 0L
        for (minor in 1L..500_000L) {
            val itemPrice = Money(minor, "GBP")
            val fee = BuyerProtectionFee.feeFor(itemPrice)
            assertTrue("fee never falls ($minor)", fee.minor >= previousFee)
            previousFee = fee.minor
            assertEquals("round trip at $minor", itemPrice, BuyerProtectionFee.itemPriceFromDisplayed(itemPrice + fee))
        }
    }

    @Test
    fun displayedAmountsBetweenReachableOnesMapToTheLowerItemPrice() {
        // In the 7% band one penny of price can add two pence to the displayed price; the skipped amount maps down.
        for (minor in 11L..410_000L) {
            val itemPrice = BuyerProtectionFee.itemPriceFromDisplayed(Money(minor, "GBP"))
            assertTrue("$minor", itemPrice + BuyerProtectionFee.feeFor(itemPrice) <= Money(minor, "GBP"))
            val nextPrice = itemPrice + Money(1, "GBP")
            assertTrue("$minor", nextPrice + BuyerProtectionFee.feeFor(nextPrice) > Money(minor, "GBP"))
        }
    }

    @Test
    fun noUkFeeOutsidePounds() {
        assertEquals(usd("0.00"), BuyerProtectionFee.feeFor(usd("100.00")))
        assertEquals(eur("100.00"), BuyerProtectionFee.itemPriceFromDisplayed(eur("100.00")))
    }

    @Test
    fun late2025PagesStateAFeeTheFormulaDoesNotGive() {
        // bidflow.verify MF3: the fixtures of 2025-11 (£15.94 incl. £0.94) and 2026-01 (£27.37 incl. £1.45) fit
        // neither published formula. The formula reads £14.80 + £1.14 and £25.64 + £1.73 into those prices, so the
        // listing keeps eBay's own figure and the formula serves only as the fallback for minimum bids.
        assertEquals(gbp("14.80"), BuyerProtectionFee.itemPriceFromDisplayed(gbp("15.94")))
        assertEquals(gbp("25.64"), BuyerProtectionFee.itemPriceFromDisplayed(gbp("27.37")))
        val page = Fixtures.itemPage("20251207184835_167906389654")
        val listing = ListingParser.parse(page, ItemRef(EbaySite.UK, "167906389654"), 0L)!!
        assertEquals(gbp("0.94"), listing.buyerProtectionFee)
        assertNotEquals(
            BuyerProtectionFee.feeFor(listing.currentPrice - listing.buyerProtectionFee!!),
            listing.buyerProtectionFee,
        )
    }
}
