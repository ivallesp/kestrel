package app.kestrel.ebay

import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** ListingParser on variants of a real page that no archived capture shows (signed in, reserve, extended bidding). */
class ListingParserEdgeCaseTest {
    private val livePage = Fixtures.itemPage("20260427110748_127820098965")
    private val liveRef = ItemRef(EbaySite.UK, "127820098965")
    private val buyBoxStart = """{"_type":"BuyBoxModule","""

    private fun parse(
        page: String,
        ref: ItemRef = liveRef,
    ): Listing? = ListingParser.parse(page, ref, fetchedAtUtcMillis = 1L)

    /** The live UK page with each marker (which must exist) replaced. */
    private fun live(vararg replacements: Pair<String, String>): Listing {
        var page = livePage
        for ((old, new) in replacements) {
            assertTrue("marker $old must exist", page.contains(old))
            page = page.replace(old, new)
        }
        return parse(page)!!
    }

    /** The live page with [field] (a JSON member, trailing comma included) added at the start of BUY_BOX. */
    private fun liveWithBuyBoxField(field: String): Listing = live(buyBoxStart to buyBoxStart + field)

    @Test
    fun nonNullExtendedBiddingMarksTheListingExtended() {
        assertFalse(parse(livePage)!!.extendedBidding)
        val extended = """"extendedBidding":{"windowSeconds":60,"extensionSeconds":60}"""
        assertTrue(live(""""extendedBidding":null""" to extended).extendedBidding)
        assertFalse(live(""""extendedBidding":null""" to """"extendedBidding":false""").extendedBidding)
    }

    @Test
    fun renderedExtendedBiddingLabelMarksTheListingExtended() {
        val label = """"text":"Bids placed in the last 1 minute reset the time to 60 seconds.""""
        val field = """"extendedBiddingInfo":{"textSpans":[{"_type":"TextSpan",$label}]},"""
        assertTrue(liveWithBuyBoxField(field).extendedBidding)
        assertTrue(parse("$livePage<div><span class=x>Extended bidding</span></div>")!!.extendedBidding)
    }

    @Test
    fun extendedBiddingTemplatesShippedOnEveryPageDoNotCount() {
        // Both bundle generations: the 2026 templates and the 2025 literal "2 minutes" texts.
        val bundles =
            """"bodyTextWindowMinuteExtensionSeconds":"Bids placed in the last 1 minute reset the time to """ +
                """{extension} seconds.","education":{"title":"Extended bidding"},""" +
                """"postWindowBodyText":"New bids reset the time left to 2 minutes.""""
        assertFalse(parse("$livePage<script>var bundle={$bundles}</script>")!!.extendedBidding)
    }

    @Test
    fun reserveWordingSetsReserveMet() {
        val hasReserve = """"hasReservePrice":false""" to """"hasReservePrice":true"""
        val reserveInfo = { text: String ->
            buyBoxStart to
                buyBoxStart +
                """"bidAdditionalInfo":[{"additionalText":{"_type":"TextualDisplay",""" +
                """"textSpans":[{"_type":"TextSpan","text":"$text"}]}}],"""
        }
        val notMet = live(hasReserve, reserveInfo("Reserve not met"))
        assertTrue(notMet.hasReserve)
        assertEquals(false, notMet.reserveMet)
        assertEquals(true, live(hasReserve, reserveInfo("Reserve met")).reserveMet)
        val silent = live(hasReserve)
        assertTrue(silent.hasReserve)
        assertNull(silent.reserveMet)
    }

    @Test
    fun signedInPageExposesTheUsersStanding() {
        val bidderDetails =
            """"extendedBidding":null,"bidderAuctionDetails":{"__typename":"BidderAuctionDetails",""" +
                """"userBiddingStatus":"WINNING"}"""
        val listing =
            live(
                """"userAuth":false""" to """"userAuth":true""",
                """"signedIn":false""" to """"signedIn":true""",
                """"isHighBidder":false""" to """"isHighBidder":true""",
                """"extendedBidding":null""" to bidderDetails,
            )
        assertEquals(true, listing.signedIn)
        assertEquals(true, listing.userIsHighBidder)
        assertEquals("WINNING", listing.userBiddingStatus)
        assertFalse(listing.extendedBidding)
    }

    @Test
    fun signedOutPageHidesTheAnonymousViewersStanding() {
        val listing = parse(livePage)!!
        assertEquals(false, listing.signedIn)
        assertNull(listing.userIsHighBidder)
        assertNull(listing.userBiddingStatus)
    }

    @Test
    fun redirectToAnotherItemReportsTheNewItemNumber() {
        val listing = parse(livePage, ItemRef(EbaySite.UK, "111111111111"))!!
        assertEquals(ItemRef(EbaySite.UK, "111111111111"), listing.ref)
        assertEquals("127820098965", listing.relistedItemId)
    }

    @Test
    fun anotherListingsAuctionDataInARecommendationIsIgnored() {
        val carousel =
            """<script>{"items":[{"__typename":"Item","itemId":"999999999999","auctionData":{""" +
                """"__typename":"AuctionData","endAtOrAfter":"2026-04-28T10:00:00Z","biddingEndedAt":null,""" +
                """"extendedBidding":{"windowSeconds":60},"bidderAuctionDetails":{"userBiddingStatus":"OUTBID"}}}]}""" +
                "</script>"
        val listing = parse(livePage + carousel)!!
        assertFalse(listing.extendedBidding)
        assertNull(listing.userBiddingStatus)
    }

    @Test
    fun buyerProtectionInTheLaterFourSpanWording() {
        val program =
            """"bidPriceProgram":{"_type":"TextualDisplay","textSpans":[{"text":"incl. "},{"text":"£0.47"},""" +
                """{"text":" "},{"text":"Buyer Protection fee"}]},"""
        assertEquals(gbp("0.47"), liveWithBuyBoxField(program).buyerProtectionFee)
        val spelledOut = """"bidPriceProgram":{"textSpans":[{"text":"Includes £0.52 Buyer Protection fee"}]},"""
        assertEquals(gbp("0.52"), liveWithBuyBoxField(spelledOut).buyerProtectionFee)
        // A fee in another currency than the price cannot be part of it.
        val otherCurrency = """"bidPriceProgram":{"textSpans":[{"text":"incl. US $0.52 for Buyer Protection"}]},"""
        assertNull(liveWithBuyBoxField(otherCurrency).buyerProtectionFee)
    }

    @Test
    fun auctionWithBuyItNowKeepsBothPrices() {
        val binModel =
            """"binModel":{"price":{"_type":"TextualDisplayValue","value":{"value":25,"currency":"GBP"},""" +
                """"textSpans":[{"text":"£25.00"}]}},"""
        val listing = liveWithBuyBoxField(binModel)
        assertTrue(listing.isAuction)
        assertEquals(gbp("5.37"), listing.currentPrice)
        assertEquals(gbp("25.00"), listing.buyItNow)
    }

    @Test
    fun fixedPriceListingTakesTheStructuredFee() {
        val page =
            Fixtures
                .itemPage("20260211084851_192336704984")
                .replace(""""binModel":{""", """"binModel":{"buyerProtectionFee":{"value":0.31,"currency":"GBP"},""")
        val listing = ListingParser.parse(page, ItemRef(EbaySite.UK, "192336704984"), 1L)!!
        assertEquals(gbp("0.31"), listing.buyerProtectionFee)
    }

    @Test
    fun endTimeFallsBackToTheTimerWhenSemanticDataHasNone() {
        val listing = live(""""endDate":{"value":"2026-04-27T11:13:22.000Z"""" to """"endDateGone":{"value":"x"""")
        assertEquals(utc("2026-04-27T11:13:22Z"), listing.endTimeUtcMillis)
    }

    @Test
    fun statusFallsBackToBiddingEndedAtWhenListingStatusIsMissing() {
        val listing =
            live(
                """"listingStatus":"ACTIVE"""" to """"listingState":"?"""",
                """"biddingEndedAt":null""" to """"biddingEndedAt":"2026-04-27T11:13:22Z"""",
            )
        assertEquals(ListingStatus.ENDED, listing.status)
    }

    @Test
    fun pagesWithoutAnItemModelGiveNull() {
        assertNull(parse(""))
        assertNull(parse("<html><head><title>Error Page | eBay</title></head><body>SORRY Something went wrong</body>"))
        val cutOff = """<title>Pardon Our Interruption...</title><script>x = {"SEMANTIC_DATA":{"broken": </script>"""
        assertNull(parse(cutOff))
    }

    @Test
    fun modelWithoutPriceOrEndTimeGivesNull() {
        val start = """<script>{"SEMANTIC_DATA":{"_type":"SemanticDataModule","listingStatus":"ACTIVE","bidCount":1"""
        assertNull(parse("$start}}</script>"))
        val price = ""","currentBidPrice":{"value":5,"currency":"GBP"}"""
        assertNull(parse("$start$price}}</script>"))
        val listing = parse("""$start$price,"endDate":{"value":"2026-05-01T10:00:00.000Z"}}}</script>""")!!
        assertEquals(gbp("5.00"), listing.currentPrice)
        assertEquals(utc("2026-05-01T10:00:00Z"), listing.endTimeUtcMillis)
        assertEquals("eBay item 127820098965", listing.title)
        assertNull(listing.signedIn)
    }

    // The public parser also swallows failures; these call the reader itself, so a throw fails the test.
    @Test
    fun truncatedPagesNeverThrow() {
        for (name in Fixtures.itemPageNames()) {
            val page = Fixtures.itemPage(name)
            for (fraction in listOf(0.05, 0.25, 0.5, 0.6, 0.75, 0.9, 0.99)) {
                // Any result is acceptable; the reader only has to fail quietly on a page cut off mid-load.
                ListingReader.read(page.substring(0, (page.length * fraction).toInt()), liveRef, 1L)
            }
        }
    }

    @Test
    fun damagedPagesNeverThrow() {
        val reduced = ReducedPage.from(livePage)
        val random = Random(20261104)
        val junk =
            listOf("{", "}", "\"", "\\", ":", ",", "[", "]", "<", ">", "&#", "\u0000", "\uD83D", "null") +
                """"SEMANTIC_DATA":{"""
        repeat(300) {
            val page = StringBuilder(reduced)
            repeat(1 + random.nextInt(6)) {
                val at = random.nextInt(page.length)
                if (random.nextBoolean()) {
                    page.delete(at, (at + random.nextInt(2_000)).coerceAtMost(page.length))
                } else {
                    page.insert(at, junk[random.nextInt(junk.size)])
                }
            }
            ListingReader.read(page.toString(), liveRef, 1L)
        }
    }

    @Test
    fun negativePriceIsRejected() {
        val listing = live(""""currentBidPrice":{"value":5.37""" to """"currentBidPrice":{"value":-5.37""")
        // SEMANTIC_DATA's negative price is ignored and BUY_BOX's value is used instead.
        assertEquals(gbp("5.37"), listing.currentPrice)
    }
}
