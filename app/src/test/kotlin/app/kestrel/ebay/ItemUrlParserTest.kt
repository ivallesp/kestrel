package app.kestrel.ebay

import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.ParsedLink
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** URL shapes from bidflow UR1-UR5 and the archived pages, one table per family. */
class ItemUrlParserTest {
    private val uk = "https://www.ebay.co.uk"
    private val us = "https://www.ebay.com"

    private fun item(
        site: EbaySite,
        itemId: String,
    ) = ParsedLink.Item(ItemRef(site, itemId))

    private fun ukItem(itemId: String) = item(EbaySite.UK, itemId)

    private fun usItem(itemId: String) = item(EbaySite.US, itemId)

    private fun short(url: String) = ParsedLink.ShortLink(url)

    private fun notListing(url: String) = ParsedLink.NotAListing(url)

    private fun assertParses(
        cases: List<Pair<String, ParsedLink>>,
        defaultSite: EbaySite = EbaySite.UK,
    ) {
        for ((input, expected) in cases) {
            assertEquals("input: $input", expected, ItemUrlParser.parse(input, defaultSite))
        }
    }

    @Test
    fun itemPathShapes() {
        assertParses(
            listOf(
                "$uk/itm/127820098965" to ukItem("127820098965"),
                "$uk/itm/127820098965/" to ukItem("127820098965"),
                "$uk/itm/-/192336704984?roken=cUgayN" to ukItem("192336704984"),
                "https://m.ebay.co.uk/itm/0-10-Amp-capable-LED-Flashlight-driver-board-17mm-Cree-XHP70-XHP50-" +
                    "XML2-XPL-/201546868150?nav=SEARCH" to ukItem("201546868150"),
                "$uk/itm/177654722949?_skw=edrums&itmmeta=01KC0AQ&hash=item295d0bf185%3Ag%3AFiUAAeSwH9ZpNbpZ" +
                    "&itmprp=enc%3A1" to ukItem("177654722949"),
                "https://ebay.co.uk/itm/127820098965" to ukItem("127820098965"),
                "http://www.ebay.com/itm/127622190770" to usItem("127622190770"),
                "HTTPS://WWW.EBAY.COM/ITM/127622190770" to usItem("127622190770"),
                "$uk/itm/127820098965#description" to ukItem("127820098965"),
                "https://www.ebay.co.uk:443/itm/127820098965" to ukItem("127820098965"),
                // 9 and 15 digits are the accepted extremes.
                "$us/itm/123456789" to usItem("123456789"),
                "$us/itm/123456789012345" to usItem("123456789012345"),
            ),
        )
    }

    @Test
    fun everySiteInEveryHostForm() {
        for (site in EbaySite.entries) {
            val domain = site.host.removePrefix("www.")
            for (host in listOf("www.$domain", "m.$domain", domain)) {
                assertEquals(host, item(site, "127820098965"), ItemUrlParser.parse("https://$host/itm/127820098965"))
            }
        }
    }

    @Test
    fun queryForms() {
        assertParses(
            listOf(
                "https://cgi.ebay.com/ws/eBayISAPI.dll?ViewItem&item=127622190770" to usItem("127622190770"),
                "http://cgi.ebay.co.uk/ws/eBayISAPI.dll?ViewItem&item=127820098965&ssPageName=ADME" to
                    ukItem("127820098965"),
                "https://www.ebay.de/ws/eBayISAPI.dll?ViewItem&itemId=147226909132" to
                    item(EbaySite.DE, "147226909132"),
                "$uk/vi?iid=127820098965" to ukItem("127820098965"),
                "$uk/bfl/viewbids/127820098965?item=127820098965&rt=nc" to ukItem("127820098965"),
            ),
        )
    }

    @Test
    fun shareTextAndAppShareLinks() {
        assertParses(
            listOf(
                "Check out this item on eBay https://ebay.us/m/100axl" to short("https://ebay.us/m/100axl"),
                "https://ebay.us/100Blw" to short("https://ebay.us/100Blw"),
                "https://ebay.io/m/MLxzM3" to short("https://ebay.io/m/MLxzM3"),
                "ebay.us/m/100aeV" to short("https://ebay.us/m/100aeV"),
                "Look at this! (https://ebay.us/m/100axl)." to short("https://ebay.us/m/100axl"),
                "Check out this item on eBay $uk/itm/317437438212?mkcid=16&mkevt=1&mkrid=711-127632-2357-0" +
                    "&ssspo=qPnNpvyUTpW&sssrc=2051273&ssuid=qPnNpvyUTpW&stype=1&var=&widget_ver=artemis&media=TW" to
                    ukItem("317437438212"),
                "Have a look: www.ebay.co.uk/itm/127820098965, ends tonight" to ukItem("127820098965"),
                "https://www.ebay.de/itm/165688561910?mkcid=16&var=465201636033&widget_ver=artemis&media=COPY" to
                    item(EbaySite.DE, "165688561910"),
                // A listing link wins over a short link and a non-listing link in the same text.
                "$uk/sch/i.html?_nkw=lego https://ebay.us/m/100axl $us/itm/127622190770" to usItem("127622190770"),
            ),
        )
    }

    @Test
    fun wrappedLinks() {
        assertParses(
            listOf(
                "https://rover.ebay.com/rover/1/710-53481-19255-0/1?icep_id=114&ipn=icep" +
                    "&mpre=https%3A%2F%2Fwww.ebay.co.uk%2Fitm%2F127820098965" to ukItem("127820098965"),
                "https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn" +
                    "&ru=https%3A%2F%2Fwww.ebay.co.uk%2Fitm%2F127820098965%3Fcta%3Dplacebid" to ukItem("127820098965"),
                "https://l.facebook.com/l.php?u=https%3A%2F%2Fwww.ebay.com%2Fitm%2F127622190770&h=AT0" to
                    usItem("127622190770"),
                "https://www.google.com/url?q=https://ebay.us/m/100axl&sa=D" to short("https://ebay.us/m/100axl"),
            ),
        )
    }

    @Test
    fun bareItemNumbers() {
        assertParses(
            listOf(
                "127820098965" to ukItem("127820098965"),
                "  127820098965\n" to ukItem("127820098965"),
                "#127820098965" to ukItem("127820098965"),
                "eBay item number: 127820098965" to ukItem("127820098965"),
                "Item number 127820098965" to ukItem("127820098965"),
                "Item no. 127820098965" to ukItem("127820098965"),
            ),
        )
        assertEquals(item(EbaySite.DE, "147226909132"), ItemUrlParser.parse("147226909132", EbaySite.DE))
        assertParses(
            listOf(
                "12345678" to ParsedLink.NotEbay,
                "1234567890123456" to ParsedLink.NotEbay,
                "Call me on 07700 900123" to ParsedLink.NotEbay,
                "127820098965 and 127622190770" to ParsedLink.NotEbay,
            ),
        )
    }

    @Test
    fun ebayPagesThatAreNotListings() {
        val unchanged =
            listOf(
                "$uk/p/404139576",
                // A catalogue page's iid= names the listing it was reached from, not the page.
                "$uk/p/404139576?iid=127820098965",
                "$uk/str/omnivinyl",
                "$us/b/Vintage-Keyboards/bn_7000259852",
                "$uk/",
                "$uk/itm/12345",
                "https://ebay.us/",
                "$uk/mye/myebay/summary",
            )
        assertParses(unchanged.map { it to notListing(it) })
        assertParses(
            listOf(
                "$uk/sch/i.html?_nkw=edrums&_sacat=0&_trksid=p4432023.m570.l1313" to
                    notListing("$uk/sch/i.html?_nkw=edrums&_sacat=0"),
            ),
        )
    }

    @Test
    fun notEbay() {
        assertParses(
            listOf(
                "" to ParsedLink.NotEbay,
                "hello" to ParsedLink.NotEbay,
                "https://example.com/itm/127820098965" to ParsedLink.NotEbay,
                "https://www.ebay.co.uk.example.com/itm/127820098965" to ParsedLink.NotEbay,
                "https://www.ebay.co.uk@example.com/itm/127820098965" to ParsedLink.NotEbay,
                "https://notebay.co.uk/itm/127820098965" to ParsedLink.NotEbay,
                "ftp://www.ebay.co.uk/itm/127820098965" to ParsedLink.NotEbay,
                "https://www.etsy.com/listing/1234567890/a-lamp" to ParsedLink.NotEbay,
            ),
        )
    }

    @Test
    fun eBaysOtherCountrySitesNameAListingOnTheHomeSite() {
        // No site entry for these, but the item number is global: the listing opens on the home site.
        val home = item(EbaySite.HOME, "127820098965")
        for (
        url in
        listOf(
            "https://www.ebay.be/itm/127820098965",
            "https://www.benl.ebay.be/itm/127820098965",
            "https://www.befr.ebay.be/itm/a-title/127820098965?_trksid=p1",
            "https://www.ebay.com.hk/itm/127820098965",
            "https://www.ebay.com.sg/itm/127820098965",
            "https://m.ebay.com.my/itm/127820098965",
            "https://www.ebay.ph/itm/127820098965",
            "Look at this ebay.com.hk/itm/127820098965",
        )
        ) {
            assertEquals(url, home, ItemUrlParser.parse(url))
        }
        assertEquals(ParsedLink.NotEbay, ItemUrlParser.parse("https://www.ebay.com.hk.example.com/itm/127820098965"))
        assertEquals(ParsedLink.NotEbay, ItemUrlParser.parse("https://notebay.ph/itm/127820098965"))
    }

    @Test
    fun arbitraryTextNeverThrows() {
        val random = Random(7)
        val pieces =
            listOf(
                "https://",
                "http://",
                "www.",
                "m.",
                "ebay.",
                "co.uk",
                "com",
                "us/m/",
                "/itm/",
                "?",
                "&",
                "=",
                "%",
                "%E2%82",
                "#",
                "@",
                ":",
                "1234567890",
                " ",
                "\n",
                "\\",
                "item=",
                "mpre=https%3A%2F%2F",
                "€",
                "\uD83D",
            )
        repeat(5_000) {
            val text = buildString { repeat(random.nextInt(1, 24)) { append(pieces[random.nextInt(pieces.size)]) } }
            ItemLinkFinder.find(text, EbaySite.UK)
        }
    }

    @Test
    fun trackingIsStrippedFromReturnedUrls() {
        val result =
            ItemUrlParser.parse(
                "$uk/sch/i.html?_nkw=lego&mkcid=1&mkrid=710-53481-19255-0&siteid=3&campid=5337256279" +
                    "&customid=x&toolid=10001&mkevt=1&utm_source=newsletter&_trksid=p2047675",
            )
        assertEquals(notListing("$uk/sch/i.html?_nkw=lego"), result)
        // A non-empty var= selects a fixed-price variation and stays; an empty one is dropped.
        val variation = ItemUrlParser.parse("$uk/sch/i.html?_nkw=lego&var=465201636033&var2=&media=COPY")
        assertEquals(notListing("$uk/sch/i.html?_nkw=lego&var=465201636033&var2="), variation)
        assertEquals(notListing("$uk/sch/i.html?_nkw=lego"), ItemUrlParser.parse("$uk/sch/i.html?_nkw=lego&var="))
    }
}
