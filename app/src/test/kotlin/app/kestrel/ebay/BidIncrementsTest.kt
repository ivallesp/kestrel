package app.kestrel.ebay

import app.kestrel.model.EbaySite
import app.kestrel.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Increment tables at every band boundary (rules.md INC1-INC4) and the minimum next bid (PX2, INC6). */
class BidIncrementsTest {
    private fun assertIncrements(
        site: EbaySite,
        currency: String,
        cases: List<Pair<String, String>>,
    ) {
        for ((price, increment) in cases) {
            assertEquals(
                "$site at $price",
                Money.of(increment, currency),
                BidIncrements.increment(site, Money.of(price, currency)),
            )
        }
    }

    @Test
    fun unitedKingdom() {
        assertIncrements(
            EbaySite.UK,
            "GBP",
            listOf(
                "0.01" to "0.05",
                "0.99" to "0.05",
                "1.00" to "0.20",
                "4.99" to "0.20",
                "5.00" to "0.50",
                "14.99" to "0.50",
                "15.00" to "1.00",
                "59.99" to "1.00",
                "60.00" to "2.00",
                "149.99" to "2.00",
                "150.00" to "5.00",
                "299.99" to "5.00",
                "300.00" to "10.00",
                "599.99" to "10.00",
                "600.00" to "20.00",
                "1499.99" to "20.00",
                "1500.00" to "50.00",
                "2999.99" to "50.00",
                "3000.00" to "100.00",
                "250000.00" to "100.00",
            ),
        )
    }

    @Test
    fun unitedStates() {
        assertIncrements(
            EbaySite.US,
            "USD",
            listOf(
                "0.01" to "0.05",
                "0.99" to "0.05",
                "1.00" to "0.25",
                "4.99" to "0.25",
                "5.00" to "0.50",
                "24.99" to "0.50",
                "25.00" to "1.00",
                "99.99" to "1.00",
                "100.00" to "2.50",
                "249.99" to "2.50",
                "250.00" to "5.00",
                "499.99" to "5.00",
                "500.00" to "10.00",
                "999.99" to "10.00",
                "1000.00" to "25.00",
                "2499.99" to "25.00",
                "2500.00" to "50.00",
                "4999.99" to "50.00",
                "5000.00" to "100.00",
                "99999.00" to "100.00",
            ),
        )
    }

    @Test
    fun germany() {
        assertIncrements(
            EbaySite.DE,
            "EUR",
            listOf(
                // ebay.de starts at 1,00; below it the first row applies.
                "0.50" to "0.50",
                "1.00" to "0.50",
                "49.99" to "0.50",
                "50.00" to "1.00",
                "499.99" to "1.00",
                "500.00" to "5.00",
                "999.99" to "5.00",
                "1000.00" to "10.00",
                "4999.99" to "10.00",
                "5000.00" to "50.00",
                "80000.00" to "50.00",
            ),
        )
    }

    @Test
    fun otherSites() {
        // Spot checks per INC1, INC3 and INC4: CA and AU equal the US, AT and CH equal DE, FR/IT/ES are coarse, NL is
        // the UK table in euros, PL is US-style in złoty; IE follows NL (no published table).
        assertEquals(Money.of("2.50", "CAD"), BidIncrements.increment(EbaySite.CA, Money.of("100.00", "CAD")))
        assertEquals(Money.of("0.25", "AUD"), BidIncrements.increment(EbaySite.AU, Money.of("1.00", "AUD")))
        assertEquals(Money.of("5.00", "EUR"), BidIncrements.increment(EbaySite.AT, Money.of("500.00", "EUR")))
        assertEquals(Money.of("1.00", "CHF"), BidIncrements.increment(EbaySite.CH, Money.of("50.00", "CHF")))
        for (site in listOf(EbaySite.FR, EbaySite.IT, EbaySite.ES)) {
            assertEquals(eur("0.50"), BidIncrements.increment(site, eur("0.01")))
            assertEquals(eur("1.00"), BidIncrements.increment(site, eur("300.00")))
        }
        assertEquals(eur("10.00"), BidIncrements.increment(EbaySite.NL, eur("300.00")))
        assertEquals(eur("0.05"), BidIncrements.increment(EbaySite.IE, eur("0.50")))
        assertEquals(Money.of("2.50", "PLN"), BidIncrements.increment(EbaySite.PL, Money.of("100.00", "PLN")))
        assertEquals(Money.of("0.25", "PLN"), BidIncrements.increment(EbaySite.PL, Money.of("0.50", "PLN")))
    }

    @Test
    fun incrementsGrowWithPriceOnEverySite() {
        for (site in EbaySite.entries) {
            var previous = 0L
            for (minor in 1L..1_000_000L step 7) {
                val increment = BidIncrements.increment(site, Money(minor, site.currency)).minor
                assertTrue("$site at $minor", increment >= previous && increment > 0)
                previous = increment
            }
        }
    }

    @Test
    fun listingFromAnotherSiteUsesItsOwnCurrencysTable() {
        // A US listing seen on ebay.co.uk is bid in dollars with the US table.
        assertEquals(usd("2.50"), BidIncrements.increment(EbaySite.UK, usd("100.00")))
        // A euro listing seen on ebay.co.uk: the larger of the euro tables.
        assertEquals(eur("10.00"), BidIncrements.increment(EbaySite.UK, eur("300.00")))
        assertEquals(eur("0.50"), BidIncrements.increment(EbaySite.UK, eur("2.00")))
    }

    @Test
    fun minimumNextBidWithoutBuyerProtection() {
        assertEquals(
            gbp("5.37"),
            BidIncrements.minimumNextBid(EbaySite.UK, gbp("5.37"), bidCount = 0, buyerProtectionIncluded = false),
        )
        assertEquals(
            gbp("5.87"),
            BidIncrements.minimumNextBid(EbaySite.UK, gbp("5.37"), bidCount = 1, buyerProtectionIncluded = false),
        )
        assertEquals(
            gbp("62.00"),
            BidIncrements.minimumNextBid(EbaySite.UK, gbp("60.00"), bidCount = 3, buyerProtectionIncluded = false),
        )
        // Gixen forum t=11405: $106.50 current, $2.50 increment above $100, so $107.55 was too low.
        assertEquals(
            usd("109.00"),
            BidIncrements.minimumNextBid(EbaySite.US, usd("106.50"), bidCount = 5, buyerProtectionIncluded = false),
        )
        assertEquals(
            eur("8.00"),
            BidIncrements.minimumNextBid(EbaySite.DE, eur("7.50"), bidCount = 4, buyerProtectionIncluded = false),
        )
        // A starting price is the minimum even with the fee inside it.
        assertEquals(
            gbp("15.94"),
            BidIncrements.minimumNextBid(EbaySite.UK, gbp("15.94"), bidCount = 0, buyerProtectionIncluded = true),
        )
    }

    @Test
    fun minimumNextBidWithUkBuyerProtectionTakesTheLargerMethod() {
        // rules.md INC6: displayed £200.20 is £191.83 + £8.37 fee. Plus the increment on the displayed price gives
        // £205.20 (Gixen moderator); the increment on the item price plus a new fee gives £205.40. The larger wins.
        assertEquals(
            gbp("205.40"),
            BidIncrements.minimumNextBid(EbaySite.UK, gbp("200.20"), bidCount = 7, buyerProtectionIncluded = true),
        )
        // Just above a band edge in displayed terms (item price £148.00, displayed £154.62): £5 on the displayed price
        // beats £2 on the item price plus fee (£156.70).
        assertEquals(
            gbp("159.62"),
            BidIncrements.minimumNextBid(EbaySite.UK, gbp("154.62"), bidCount = 2, buyerProtectionIncluded = true),
        )
        // Never below the plain rule.
        for (minor in 11L..400_000L step 37) {
            val current = Money(minor, "GBP")
            val plain = BidIncrements.minimumNextBid(EbaySite.UK, current, 1, buyerProtectionIncluded = false)
            val withFee = BidIncrements.minimumNextBid(EbaySite.UK, current, 1, buyerProtectionIncluded = true)
            assertTrue("$minor", withFee >= plain)
        }
    }

    @Test
    fun buyerProtectionFlagIgnoredOutsideGbp() {
        assertEquals(
            usd("70.99"),
            BidIncrements.minimumNextBid(EbaySite.UK, usd("69.99"), bidCount = 1, buyerProtectionIncluded = true),
        )
    }
}
