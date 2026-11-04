package app.kestrel.ebay

import app.kestrel.model.AutoRefreshState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AutoRefreshParser over the 96 archived AUTO_REFRESH replies (ebay.com, ebay.co.uk, ebay.de, 2024-01 to 2026-04). */
class AutoRefreshParserTest {
    @Test
    fun everyReplyParsesToSaneValues() {
        val files = Fixtures.autoRefreshFiles()
        assertEquals(96, files.size)
        for (file in files) {
            val name = file.nameWithoutExtension
            val state = AutoRefreshParser.parse(file.readText())
            assertNotNull(name, state)
            state!!
            val price = state.currentPrice
            assertNotNull("$name price", price)
            assertTrue("$name price ${price!!.minor}", price.minor in 1..10_000_000)
            assertTrue("$name currency ${price.currency}", price.currency in setOf("GBP", "USD", "EUR"))
            assertTrue("$name bid count", state.bidCount!! in 0..100)
            assertEquals("$name: ended replies carry no refresh delay", state.ended, state.refreshInSeconds == null)
            val end = state.endTimeUtcMillis ?: continue
            val captured = Fixtures.captureMillis(name)
            val secondsLeft = (end - captured) / 1000
            // Captures at or after the end are exactly the ones eBay marks as ended.
            assertEquals("$name ended with ${secondsLeft}s left", secondsLeft <= 0, state.ended)
            state.refreshInSeconds?.let { refresh ->
                assertTrue("$name refresh $refresh with ${secondsLeft}s left", refresh <= maxOf(secondsLeft, 60))
            }
        }
    }

    @Test
    fun liveUkReplyFiveMinutesOut() {
        assertEquals(
            AutoRefreshState(gbp("5.37"), 1, utc("2026-04-27T11:13:22Z"), refreshInSeconds = 16, ended = false),
            parse("20260427110806_www.ebay.co.uk_127820098965"),
        )
    }

    @Test
    fun replyAtTheEndSecondIsEnded() {
        assertEquals(
            AutoRefreshState(gbp("195.00"), 9, utc("2026-04-03T09:39:33Z"), refreshInSeconds = null, ended = true),
            parse("20260403093933_www.ebay.co.uk_157799545552"),
        )
        assertEquals(
            AutoRefreshState(usd("23.50"), 12, utc("2024-01-22T01:20:01Z"), refreshInSeconds = null, ended = true),
            parse("20240122012002_www.ebay.com_126282221028"),
        )
    }

    @Test
    fun germanReplyReadsValuesNotLocalizedText() {
        assertEquals(
            AutoRefreshState(eur("1.00"), 1, utc("2026-02-17T17:55:56Z"), refreshInSeconds = 6, ended = false),
            parse("20260217175327_www.ebay.de_206055686082"),
        )
        assertEquals(
            AutoRefreshState(eur("7.50"), 4, utc("2026-04-03T18:20:04Z"), refreshInSeconds = 3, ended = false),
            parse("20260403181818_www.ebay.de_147226909132"),
        )
    }

    @Test
    fun convertedPriceIsReportedInTheListingCurrency() {
        // A GBP listing seen on ebay.com: value 11.51 USD, convertedFromValue 8.51 GBP.
        assertEquals(gbp("8.51"), parse("20250926184314_www.ebay.com_127381934130").currentPrice)
    }

    @Test
    fun early2024WrapperWithoutTimer() {
        // Jan 2024: an extra x-hero state and no endTime in the bid-price section.
        assertEquals(
            AutoRefreshState(gbp("160.00"), 11, endTimeUtcMillis = null, refreshInSeconds = 3, ended = false),
            parse("20240109160211_www.ebay.co.uk_204601168702"),
        )
        // Only the bid-price section is left once the auction is over.
        assertEquals(
            AutoRefreshState(gbp("72.00"), 9, endTimeUtcMillis = null, refreshInSeconds = null, ended = true),
            parse("20240218161931_www.ebay.co.uk_196238862526"),
        )
    }

    @Test
    fun refreshDelayFollowsTimeLeft() {
        assertEquals(2050, parse("20250713174723_www.ebay.com_127212779949").refreshInSeconds)
        assertEquals(59, parse("20251214171903_www.ebay.co.uk_177654722949").refreshInSeconds)
        assertEquals(2, parse("20240318195955_www.ebay.co.uk_116095804554").refreshInSeconds)
        val nearTheEnd = parse("20260121005853_www.ebay.com_127622190770")
        assertEquals(AutoRefreshState(usd("80.48"), 5, utc("2026-01-21T01:00:44Z"), 3, false), nearTheEnd)
    }

    @Test
    fun toleratesNewWrappersAroundTheStates() {
        val original = Fixtures.autoRefresh("20260427110806_www.ebay.co.uk_127820098965")
        val wrapped = "{\"data\":{\"refresh\":$original}}"
        assertEquals(parse("20260427110806_www.ebay.co.uk_127820098965"), AutoRefreshParser.parse(wrapped))
        // Even with the state renamed beyond recognition, the payload is found by the fields it carries.
        val renamedEvents =
            original.replace(
                "ux-app__x-bid-price-section__refreshState",
                "ux-app__x-price-panel__state",
            )
        assertTrue(renamedEvents != original)
        assertEquals(parse("20260427110806_www.ebay.co.uk_127820098965"), AutoRefreshParser.parse(renamedEvents))
    }

    @Test
    fun timerLabelReadingTheExpiredTextMeansEnded() {
        val original = Fixtures.autoRefresh("20260427110806_www.ebay.co.uk_127820098965")
        val expiredLabel =
            original.replace(
                "\"label\":{\"_type\":\"TextSpan\",\"text\":\"Ends in\"}",
                "\"label\":{\"_type\":\"TextSpan\",\"text\":\"Ended\"}",
            )
        assertTrue(expiredLabel != original)
        val state = AutoRefreshParser.parse(expiredLabel)!!
        assertTrue(state.ended)
        assertNull(state.refreshInSeconds)
    }

    @Test
    fun missingCountdownWhileEbayStillAsksForARefreshIsNotAnEnd() {
        val original = Fixtures.autoRefresh("20260427110806_www.ebay.co.uk_127820098965")
        val withoutCountdown = original.replace("\"text\":\"5m 15s\"", "\"text\":\"\"")
        assertTrue(withoutCountdown != original)
        val state = AutoRefreshParser.parse(withoutCountdown)!!
        assertFalse(state.ended)
        assertEquals(16, state.refreshInSeconds)
    }

    @Test
    fun notAnAutoRefreshReply() {
        assertNull(AutoRefreshParser.parse(""))
        assertNull(AutoRefreshParser.parse("<html><title>Error Page | eBay</title></html>"))
        assertNull(AutoRefreshParser.parse("{\"states\":[],\"diagnostics\":{}}"))
        assertNull(
            AutoRefreshParser.parse("{\"states\":[{\"eventName\":\"ux-core__ux-call-to-action__refreshState\"}]}"),
        )
        assertNull(AutoRefreshParser.parse("[1,2,3]"))
        assertNull(
            AutoRefreshParser.parse(
                "{\"states\":[{\"eventName\":\"ux-app__x-bid-price-section__refreshState\",\"state\":{",
            ),
        )
    }

    @Test
    fun cutOffRepliesNeverThrow() {
        // The reader itself, not the guarded public parser, so a throw fails the test.
        for (file in Fixtures.autoRefreshFiles().filterIndexed { index, _ -> index % 8 == 0 }) {
            val reply = file.readText()
            for (end in reply.indices step 41) AutoRefreshReader.read(reply.substring(0, end))
        }
    }

    @Test
    fun urlFromThePageModel() {
        val expected =
            "/itemmodules/127820098965?module_groups=AUTO_REFRESH&viewer_item_relation=0&isSemiPersistentBin=false" +
                "&upiState=notEligible&upiDelayTime=0&upiDelayReason=NotApplicable&includeFGQL=false"
        assertEquals(expected, AutoRefreshParser.urlFromPage(Fixtures.itemPage("20260427110748_127820098965")))
        assertEquals(
            expected.replace("127820098965", "127622190770"),
            AutoRefreshParser.urlFromPage(Fixtures.itemPage("20260121005928_127622190770")),
        )
        // Ended and fixed-price pages have no AUTOREFRESH module.
        assertNull(AutoRefreshParser.urlFromPage(Fixtures.itemPage("20260123010506_127622190770")))
        assertNull(AutoRefreshParser.urlFromPage(Fixtures.itemPage("20260211084851_192336704984")))
    }

    @Test
    fun urlFromThePageOnlyPointsAtEbay() {
        fun page(url: String) =
            """<script>{"AUTOREFRESH":{"_type":"AutoRefreshModule","autoRefreshAction":{"URL":"$url"}}}</script>"""
        assertEquals(
            "/itemmodules/1?module_groups=AUTO_REFRESH",
            AutoRefreshParser.urlFromPage(page("/itemmodules/1?module_groups=AUTO_REFRESH")),
        )
        assertEquals(
            "https://www.ebay.co.uk/itemmodules/1",
            AutoRefreshParser.urlFromPage(page("https://www.ebay.co.uk/itemmodules/1")),
        )
        assertNull(AutoRefreshParser.urlFromPage(page("//evil.example/x")))
        assertNull(AutoRefreshParser.urlFromPage(page("https://evil.example/itemmodules/1")))
        assertNull(AutoRefreshParser.urlFromPage(page("javascript:alert(1)")))
        assertNull(AutoRefreshParser.urlFromPage("no model here"))
    }

    @Test
    fun everyLiveItemPageEmbedsTheSameKindOfUrl() {
        for (name in Fixtures.itemPageNames()) {
            val url = AutoRefreshParser.urlFromPage(Fixtures.itemPage(name)) ?: continue
            assertTrue(name, url.startsWith("/itemmodules/${name.substringAfter('_')}?module_groups=AUTO_REFRESH"))
        }
        assertFalse(AutoRefreshParser.urlFromPage(Fixtures.itemPage("20260114234459_127622190770")).isNullOrEmpty())
    }

    private fun parse(name: String): AutoRefreshState = AutoRefreshParser.parse(Fixtures.autoRefresh(name))!!
}
