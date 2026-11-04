package app.kestrel.web

import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.PageKind
import app.kestrel.model.ParsedLink
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingFetchTest {
    private val drivers = mutableListOf<FakePageDriver>()
    private val parsers = FakeParsers()
    private var landingUrl = "https://www.ebay.co.uk/itm/127820098965"
    private var landingTitle = "Britain after Rome | eBay UK"
    private var loadOutcome: LoadOutcome = LoadOutcome.Loaded(null, "complete")

    private fun TestScope.fetcher() =
        ListingFetch(
            newDriver = { newDriver() },
            script = PageScript("var VERSION = '1';"),
            parsers = parsers,
            utcClock = { TestClock(testScheduler).nowUtcMillis() },
        )

    private fun newDriver(): FakePageDriver {
        val driver = FakePageDriver()
        driver.loadResult = { url -> LoadResult(url, landingUrl, landingTitle, emptyList(), loadOutcome) }
        driver.on("probe") {
            json(
                mapOf(
                    "ok" to true,
                    "url" to landingUrl,
                    "title" to landingTitle,
                    "readyState" to "complete",
                ),
            )
        }
        driver.on(
            "modelText",
        ) { json(mapOf("ok" to true, "text" to "<title>$landingTitle</title>", "modelScripts" to 1)) }
        drivers += driver
        return driver
    }

    @Test
    fun `an auction loads and the WebView is released`() =
        runTest {
            val result = fetcher().fetch(ParsedLink.Item(ukItem))
            assertEquals(FetchResult.Loaded(sampleListing(fetchedAtUtcMillis = TestClock.UTC_START)), result)
            assertEquals(listOf(ukItem.url), drivers.single().loads)
            assertTrue(drivers.single().destroyed)
        }

    @Test
    fun `a fixed-price listing is not an auction`() =
        runTest {
            parsers.listingFor = { _, ref, at -> sampleListing(ref, isAuction = false, fetchedAtUtcMillis = at) }
            val result = fetcher().fetch(ParsedLink.Item(ukItem)) as FetchResult.NotAnAuction
            assertEquals(false, result.listing?.isAuction)
        }

    @Test
    fun `a short link is resolved by following eBay's redirect in the WebView`() =
        runTest {
            landingUrl = "https://www.ebay.co.uk/itm/317437438212?mkcid=16&mkevt=1&media=TW"
            val result = fetcher().fetch(ParsedLink.ShortLink("https://ebay.us/m/100axl")) as FetchResult.Loaded
            assertEquals(ItemRef(EbaySite.UK, "317437438212"), result.listing.ref)
            assertEquals(listOf("https://ebay.us/m/100axl"), drivers.single().loads)
        }

    @Test
    fun `a short link that lands on a non-listing page is not an auction`() =
        runTest {
            landingUrl = "https://www.ebay.co.uk/str/somestore"
            assertEquals(FetchResult.NotAnAuction(null), fetcher().fetch(ParsedLink.ShortLink("https://ebay.us/m/abc")))
        }

    @Test
    fun `a sign-in page blocks the fetch and says where it landed`() =
        runTest {
            landingUrl = "https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn&ru=x"
            landingTitle = "Sign in or Register | eBay"
            assertEquals(FetchResult.Blocked(PageKind.SIGN_IN, landingUrl), fetcher().fetch(ParsedLink.Item(ukItem)))
            assertTrue(drivers.single().destroyed)
        }

    @Test
    fun `a captcha blocks the fetch`() =
        runTest {
            landingUrl = "https://www.ebay.co.uk/splashui/captcha?ap=1&appName=orch"
            landingTitle = "Security Measure"
            assertEquals(FetchResult.Blocked(PageKind.CAPTCHA, landingUrl), fetcher().fetch(ParsedLink.Item(ukItem)))
        }

    @Test
    fun `a gone listing fails with a plain reason`() =
        runTest {
            landingUrl = "https://www.ebay.co.uk/p/123456"
            assertEquals(
                FetchResult.Failed("eBay has no listing at this link"),
                fetcher().fetch(ParsedLink.Item(ukItem)),
            )
        }

    @Test
    fun `a network failure fails and still releases the WebView`() =
        runTest {
            loadOutcome = LoadOutcome.NetworkError(-2, "net::ERR_INTERNET_DISCONNECTED")
            val result = fetcher().fetch(ParsedLink.Item(ukItem)) as FetchResult.Failed
            assertTrue(result.reason.contains("ERR_INTERNET_DISCONNECTED"))
            assertTrue(drivers.single().destroyed)
        }

    @Test
    fun `a page without an item model fails`() =
        runTest {
            parsers.listingFor = { _, _, _ -> null }
            val result = fetcher().fetch(ParsedLink.Item(ukItem)) as FetchResult.Failed
            assertTrue(result.reason.contains("could not read the listing"))
        }

    @Test
    fun `links that are not listings never open a WebView`() =
        runTest {
            assertEquals(
                FetchResult.NotAnAuction(null),
                fetcher().fetch(ParsedLink.NotAListing("https://www.ebay.co.uk/sch/i.html?_nkw=x")),
            )
            assertEquals(FetchResult.Failed("This is not an eBay link"), fetcher().fetch(ParsedLink.NotEbay))
            assertTrue(drivers.isEmpty())
        }
}
