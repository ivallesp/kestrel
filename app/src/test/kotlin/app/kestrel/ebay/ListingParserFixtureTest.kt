package app.kestrel.ebay

import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ListingParser over every archived item page. Each expected value was read from the fixture itself (SEMANTIC_DATA,
 * BUY_BOX, STATUS_MESSAGE, JSON-LD and the postage summary). All captures are signed out (Wayback crawler in the US),
 * so postage is what eBay showed that crawler.
 */
class ListingParserFixtureTest {
    @Test
    fun everyFixtureHasAnExpectedListing() {
        assertEquals(EXPECTED.keys.sorted(), Fixtures.itemPageNames())
    }

    @Test
    fun parsesEveryFixtureExactly() {
        for (name in Fixtures.itemPageNames()) {
            val expected = EXPECTED.getValue(name)
            val parsed = ListingParser.parse(Fixtures.itemPage(name), expected.ref, expected.fetchedAtUtcMillis)
            assertEquals("fixture $name", expected, parsed)
        }
    }

    @Test
    fun reducedModelTextGivesTheSameListingAsTheFullPage() {
        for (name in Fixtures.itemPageNames()) {
            val expected = EXPECTED.getValue(name)
            val fullPage = Fixtures.itemPage(name)
            val reduced = ReducedPage.from(fullPage)
            assertTrue("fixture $name: reduced page should be much smaller", reduced.length < fullPage.length * 3 / 4)
            val fromReduced = ListingParser.parse(reduced, expected.ref, expected.fetchedAtUtcMillis)
            val fromFullPage = ListingParser.parse(fullPage, expected.ref, expected.fetchedAtUtcMillis)
            assertEquals("fixture $name", fromFullPage, fromReduced)
            assertNotNull(fromReduced)
        }
    }

    @Test
    fun crossSiteListingIsPricedInTheListingCurrencyWithTheSiteConversionAlongside() {
        val listing = parse("20251209135725_146949522734")
        assertEquals(usd("24.99"), listing.currentPrice)
        assertEquals(gbp("18.76"), listing.currentPriceInSiteCurrency)
        // JSON-LD has one rate per destination; the UK one (in dollars) is taken, not the US viewer's.
        assertEquals(usd("19.95"), listing.postage)
    }

    @Test
    fun ukPrivateSellerFeeIsReadFromTheBuyBoxText() {
        assertEquals(gbp("0.94"), parse("20251207184835_167906389654").buyerProtectionFee)
        assertEquals(gbp("1.45"), parse("20260105125151_177654722949").buyerProtectionFee)
    }

    @Test
    fun sellerEndingTheAuctionEarlyIsDetected() {
        val listing = parse("20250623154440_146521106925")
        assertEquals(ListingStatus.ENDED, listing.status)
        assertTrue(listing.endedEarly)
        assertTrue(listing.scheduledEndUtcMillis!! - listing.endTimeUtcMillis > 4 * 24 * 3600 * 1000L)
    }

    @Test
    fun fixedPriceListingsAreNotAuctions() {
        val fixedPrice =
            listOf("20250709071600_186985307627", "20251207004426_143537407360", "20260211084851_192336704984")
        for (name in fixedPrice) {
            val listing = parse(name)
            assertEquals(name, false, listing.isAuction)
            assertEquals(name, listing.currentPrice, listing.buyItNow)
        }
    }

    @Test
    fun liveAuctionsAreActiveAndLeaveTheEndedFieldsEmpty() {
        val live = listOf("20260114234459_127622190770", "20260121005928_127622190770", "20260427110748_127820098965")
        for (name in live) {
            val listing = parse(name)
            assertEquals(name, ListingStatus.ACTIVE, listing.status)
            assertEquals(name, null, listing.endedText)
            assertEquals(name, false, listing.endedEarly)
            assertEquals(name, false, listing.extendedBidding)
        }
    }

    private fun parse(name: String): Listing {
        val expected = EXPECTED.getValue(name)
        return ListingParser.parse(Fixtures.itemPage(name), expected.ref, expected.fetchedAtUtcMillis)!!
    }

    private companion object {
        fun expected(
            name: String,
            site: EbaySite,
            title: String,
            imageUrl: String,
            sellerName: String,
            isAuction: Boolean = true,
            status: ListingStatus,
            end: String,
            scheduledEnd: String?,
            currentPrice: Money,
            currentPriceInSiteCurrency: Money? = null,
            bidCount: Int,
            postage: Money?,
            buyerProtectionFee: Money? = null,
            buyItNow: Money? = null,
            endedText: String? = null,
            endedEarly: Boolean = false,
            relistedItemId: String? = null,
        ): Pair<String, Listing> =
            name to
                Listing(
                    ref = ItemRef(site, name.substringAfter('_')),
                    title = title,
                    imageUrl = imageUrl,
                    sellerName = sellerName,
                    isAuction = isAuction,
                    status = status,
                    endTimeUtcMillis = utc(end),
                    scheduledEndUtcMillis = scheduledEnd?.let(::utc),
                    currentPrice = currentPrice,
                    currentPriceInSiteCurrency = currentPriceInSiteCurrency,
                    bidCount = bidCount,
                    hasReserve = false,
                    reserveMet = null,
                    postage = postage,
                    buyerProtectionFee = buyerProtectionFee,
                    buyItNow = buyItNow,
                    extendedBidding = false,
                    // Every capture is signed out, so eBay exposes no standing for the user.
                    userIsHighBidder = null,
                    userBiddingStatus = null,
                    endedText = endedText,
                    endedEarly = endedEarly,
                    signedIn = false,
                    relistedItemId = relistedItemId,
                    fetchedAtUtcMillis = Fixtures.captureMillis(name),
                )

        private const val IMAGES = "https://i.ebayimg.com/images/g"
        private const val RELISTED = " The seller has relisted this item or one like this."

        val EXPECTED: Map<String, Listing> =
            mapOf(
                // A US listing seen on ebay.co.uk, ended early by the seller: endDate is days before itemEnding.
                expected(
                    name = "20250623154440_146521106925",
                    site = EbaySite.UK,
                    title = "Autographed/Signed BRANDON INGRAM New Orleans Pelicans 16x20 Photo Fanatics COA",
                    imageUrl = "$IMAGES/bb8AAeSwNOxn~zOI/s-l1600.jpg",
                    sellerName = "shophofsm",
                    status = ListingStatus.ENDED,
                    end = "2025-04-18T15:34:01Z",
                    scheduledEnd = "2025-04-23T04:31:13Z",
                    currentPrice = usd("9.99"),
                    currentPriceInSiteCurrency = gbp("7.43"),
                    bidCount = 0,
                    // JSON-LD gives the converted £7.43; the postage summary's own figure is in the listing currency.
                    postage = usd("10.00"),
                    endedText =
                        "This listing was ended by the seller on Fri, 18 Apr at 4:34 PM because the item is " +
                            "no longer available.",
                    endedEarly = true,
                ),
                // Fixed price with Best Offer, active.
                expected(
                    name = "20250709071600_186985307627",
                    site = EbaySite.UK,
                    title = "ADRIAN NEWEY & CHRISTIAN HORNER HAND SIGNED 12X8” PHOTO RED BULL RACING F1 PROOF",
                    imageUrl = "$IMAGES/3y0AAeSwCJ5nuy0i/s-l1600.jpg",
                    sellerName = "jjc-autographs",
                    isAuction = false,
                    status = ListingStatus.ACTIVE,
                    end = "2025-07-23T13:14:24Z",
                    scheduledEnd = null,
                    currentPrice = gbp("41.92"),
                    bidCount = 0,
                    postage = null,
                    buyItNow = gbp("41.92"),
                ),
                // Ended without bids; the seller relisted it.
                expected(
                    name = "20250710210226_187278947353",
                    site = EbaySite.UK,
                    title = "Henry Cooper Autographed School Programme",
                    imageUrl = "$IMAGES/4xwAAOSwMr1oPE4g/s-l1600.jpg",
                    sellerName = "helenbbrincat",
                    status = ListingStatus.ENDED,
                    end = "2025-06-08T13:02:41Z",
                    scheduledEnd = "2025-06-08T13:02:41Z",
                    currentPrice = gbp("6.00"),
                    bidCount = 0,
                    postage = null,
                    endedText = "Bidding ended on Sun, 8 Jun at 2:02 PM.$RELISTED",
                    relistedItemId = "187308446715",
                ),
                expected(
                    name = "20250714035904_146589404386",
                    site = EbaySite.UK,
                    title = "Signed Henry Cooper Autographed Page Boxing",
                    imageUrl = "$IMAGES/FFgAAOSwJaNgVIpq/s-l1600.jpg",
                    sellerName = "ljj196668",
                    status = ListingStatus.ENDED,
                    end = "2025-05-27T20:05:58Z",
                    scheduledEnd = "2025-05-27T20:05:58Z",
                    currentPrice = gbp("15.00"),
                    bidCount = 0,
                    postage = null,
                    endedText = "Bidding ended on Tue, 27 May at 9:05 PM.$RELISTED",
                    relistedItemId = "146693414476",
                ),
                // A US listing seen on ebay.co.uk, ended and relisted.
                expected(
                    name = "20250716174824_177164760943",
                    site = EbaySite.UK,
                    title = "Sugar Ray Leonard Signed Everlast Boxing Glove",
                    imageUrl = "$IMAGES/oesAAOSwNfBoQAv6/s-l1600.jpg",
                    sellerName = "cubs1060",
                    status = ListingStatus.ENDED,
                    end = "2025-06-11T23:45:00Z",
                    scheduledEnd = "2025-06-11T23:45:00Z",
                    currentPrice = usd("44.99"),
                    currentPriceInSiteCurrency = gbp("33.61"),
                    bidCount = 0,
                    postage = usd("7.99"),
                    endedText = "Bidding ended on Wed, 11 Jun at 12:45 AM.$RELISTED",
                    relistedItemId = "177265353716",
                ),
                // Sold with one bid.
                expected(
                    name = "20250803122834_127095470088",
                    site = EbaySite.UK,
                    title = "David Price Signed Boxing Glove - Lonsdale Autograph",
                    imageUrl = "$IMAGES/OJYAAOSwjOBoGeGl/s-l1600.jpg",
                    sellerName = "icons-memorabilia",
                    status = ListingStatus.ENDED,
                    end = "2025-05-09T10:17:41Z",
                    scheduledEnd = "2025-05-09T10:17:41Z",
                    currentPrice = gbp("15.00"),
                    bidCount = 1,
                    postage = gbp("20.07"),
                    endedText = "Item sold on Fri, 9 May at 11:17 AM.",
                ),
                // Fixed price ended by the seller (no scheduled end; the banner says so).
                expected(
                    name = "20251207004426_143537407360",
                    site = EbaySite.UK,
                    title = "Camper Van Cushion Covers Made To Meausre *Including Fabric*",
                    imageUrl = "$IMAGES/kgAAAOSwubJeTmoT/s-l1600.jpg",
                    sellerName = "thesewingangel2020",
                    isAuction = false,
                    status = ListingStatus.ENDED,
                    end = "2025-12-01T22:14:34Z",
                    scheduledEnd = null,
                    currentPrice = gbp("70.00"),
                    bidCount = 0,
                    postage = gbp("15.00"),
                    buyItNow = gbp("70.00"),
                    endedText =
                        "This listing was ended by the seller on Mon, 1 Dec at 10:14 PM because the item is " +
                            "no longer available.",
                    endedEarly = true,
                ),
                // UK private seller: "incl. £0.94 for Buyer Protection" on £15.94.
                expected(
                    name = "20251207184835_167906389654",
                    site = EbaySite.UK,
                    title = "Spencer Davis Group Original 1965 Performance Contract Signed S.D.N Davis Rare",
                    imageUrl = "$IMAGES/tJAAAeSwoVBpCwt-/s-l1600.jpg",
                    sellerName = "rosefield61",
                    status = ListingStatus.ENDED,
                    end = "2025-11-16T19:08:01Z",
                    scheduledEnd = "2025-11-16T19:08:01Z",
                    currentPrice = gbp("15.94"),
                    bidCount = 0,
                    postage = null,
                    buyerProtectionFee = gbp("0.94"),
                    endedText = "Bidding ended on Sun, 16 Nov at 7:08 PM.",
                ),
                // A US listing seen on ebay.co.uk with JSON-LD rates to the US (in pounds) and to the UK (in dollars).
                expected(
                    name = "20251209135725_146949522734",
                    site = EbaySite.UK,
                    title = "ARCHIE GRIFFIN Autographed 8.5x11 Dual Heisman Trophies -Chip Smith Collection",
                    imageUrl = "$IMAGES/~TsAAeSwagNpD3Y5/s-l1600.jpg",
                    sellerName = "highfive99",
                    status = ListingStatus.ENDED,
                    end = "2025-11-17T02:10:01Z",
                    scheduledEnd = "2025-11-17T02:10:01Z",
                    currentPrice = usd("24.99"),
                    currentPriceInSiteCurrency = gbp("18.76"),
                    bidCount = 0,
                    postage = usd("19.95"),
                    endedText = "Bidding ended on Sun, 16 Nov at 2:10 AM.$RELISTED",
                    relistedItemId = "146969921734",
                ),
                // Sold after four bids, UK private seller.
                expected(
                    name = "20260105125151_177654722949",
                    site = EbaySite.UK,
                    title = "Roland FD-1 Hi-Hat Control Pedal for Electronic Drums TD-02/07 Series",
                    imageUrl = "$IMAGES/FiUAAeSwH9ZpNbpZ/s-l1600.jpg",
                    sellerName = "olimox_123",
                    status = ListingStatus.ENDED,
                    end = "2025-12-14T17:35:57Z",
                    scheduledEnd = "2025-12-14T17:35:57Z",
                    currentPrice = gbp("27.37"),
                    bidCount = 4,
                    postage = null,
                    buyerProtectionFee = gbp("1.45"),
                    endedText = "Item sold on Sun, 14 Dec at 5:35 PM.",
                ),
                // The same US auction six days out, 76 seconds out, and after it sold.
                expected(
                    name = "20260114234459_127622190770",
                    site = EbaySite.US,
                    title = "IBM Model M 1391401 'Clicky' Mechanical Keyboard, Used, Working, Vintage",
                    imageUrl = "$IMAGES/gNUAAeSwxZxpP5Lf/s-l1600.jpg",
                    sellerName = "a1usales",
                    status = ListingStatus.ACTIVE,
                    end = "2026-01-21T01:00:44Z",
                    scheduledEnd = "2026-01-21T01:00:44Z",
                    currentPrice = usd("69.99"),
                    bidCount = 1,
                    postage = usd("39.50"),
                ),
                expected(
                    name = "20260121005928_127622190770",
                    site = EbaySite.US,
                    title = "IBM Model M 1391401 'Clicky' Mechanical Keyboard, Used, Working, Vintage",
                    imageUrl = "$IMAGES/gNUAAeSwxZxpP5Lf/s-l1600.jpg",
                    sellerName = "a1usales",
                    status = ListingStatus.ACTIVE,
                    end = "2026-01-21T01:00:44Z",
                    scheduledEnd = "2026-01-21T01:00:44Z",
                    currentPrice = usd("87.00"),
                    bidCount = 8,
                    postage = usd("41.05"),
                ),
                expected(
                    name = "20260123010506_127622190770",
                    site = EbaySite.US,
                    title = "IBM Model M 1391401 'Clicky' Mechanical Keyboard, Used, Working, Vintage",
                    imageUrl = "$IMAGES/gNUAAeSwxZxpP5Lf/s-l1600.jpg",
                    sellerName = "a1usales",
                    status = ListingStatus.ENDED,
                    end = "2026-01-21T01:00:44Z",
                    scheduledEnd = "2026-01-21T01:00:44Z",
                    currentPrice = usd("93.00"),
                    bidCount = 13,
                    postage = usd("41.05"),
                    endedText = "Item sold on Tue, Jan 20 at 5:00 PM.",
                ),
                // Fixed price with Best Offer; JSON-LD rates to the US and (free) to the UK.
                expected(
                    name = "20260211084851_192336704984",
                    site = EbaySite.UK,
                    title = "Beauty and The Beard Wine Beer Glass Vinyl Decal Sticker",
                    imageUrl = "$IMAGES/CMoAAOSwfjRZ5ie9/s-l1600.png",
                    sellerName = "omnivinyl1",
                    isAuction = false,
                    status = ListingStatus.ACTIVE,
                    end = "2026-02-17T17:06:01Z",
                    scheduledEnd = null,
                    currentPrice = gbp("2.99"),
                    bidCount = 0,
                    postage = gbp("0.00"),
                    buyItNow = gbp("2.99"),
                ),
                // Live UK auction five and a half minutes before the end.
                expected(
                    name = "20260427110748_127820098965",
                    site = EbaySite.UK,
                    title = "Britain after Rome : The Fall and Rise, 400-1070 by Robin Fleming (2011,...",
                    imageUrl = "$IMAGES/mpkAAeSw8oNp5gnF/s-l1600.jpg",
                    sellerName = "simonb4423",
                    status = ListingStatus.ACTIVE,
                    end = "2026-04-27T11:13:22Z",
                    scheduledEnd = "2026-04-27T11:13:22Z",
                    currentPrice = gbp("5.37"),
                    bidCount = 1,
                    postage = gbp("2.00"),
                ),
            )
    }
}

/**
 * Builds the reduced page kestrel.js `modelText()` produces: the document title, the og:title/og:image/og:url meta
 * tags, the canonical link, the JSON-LD scripts and the inline scripts that carry a page-model module key.
 */
object ReducedPage {
    fun from(fullPage: String): String {
        val parts = mutableListOf<String>()
        TITLE.find(fullPage)?.let { parts += "<title>${it.groupValues[1]}</title>" }
        for (match in META.findAll(fullPage)) {
            parts += """<meta property="${match.groupValues[1]}" content="${match.groupValues[2]}">"""
        }
        CANONICAL.find(fullPage)?.let { parts += """<link rel="canonical" href="${it.groupValues[1]}">""" }
        for (match in SCRIPT.findAll(fullPage)) {
            val attributes = match.groupValues[1]
            val body = match.groupValues[2]
            when {
                attributes.contains("ld+json") -> parts += """<script type="application/ld+json">$body</script>"""
                MODULE_KEYS.any { it in body } -> parts += "<script>$body</script>"
            }
        }
        return parts.joinToString("\n")
    }

    private val MODULE_KEYS =
        listOf(
            """"SEMANTIC_DATA":{""",
            """"BUY_BOX":{""",
            """"STATUS_MESSAGE":{""",
            """"AUTOREFRESH":{""",
            "listingV2ById",
            "pixListingById",
        )
    private val TITLE = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val META = Regex("<meta Property=\"(og:(?:title|image|url))\" Content=\"([^\"]*)\"")
    private val CANONICAL = Regex("<link rel=\"canonical\" href=\"([^\"]*)\"")
    private val SCRIPT = Regex("<script([^>]*)>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
}
