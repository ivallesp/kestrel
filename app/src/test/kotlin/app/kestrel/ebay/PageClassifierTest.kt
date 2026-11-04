package app.kestrel.ebay

import app.kestrel.model.PageKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** URLs and titles from bidflow AB1, AB4, AB6 and RS6 plus the archived pages. */
class PageClassifierTest {
    private fun assertKinds(cases: List<Triple<String, String, PageKind>>) {
        for ((url, title, expected) in cases) {
            assertEquals(
                "$url | $title",
                expected,
                PageClassifier.classify(url, title),
            )
        }
    }

    @Test
    fun itemAndOtherNormalPages() {
        assertKinds(
            listOf(
                Triple(
                    "https://www.ebay.co.uk/itm/127820098965",
                    "Britain after Rome : The Fall and Rise, 400-1070 by Robin Fleming (2011,...  | eBay UK",
                    PageKind.NORMAL,
                ),
                Triple(
                    "https://www.ebay.com/itm/127622190770",
                    "IBM Model M 1391401 'Clicky' Mechanical Keyboard, Used, Working, Vintage | eBay",
                    PageKind.NORMAL,
                ),
                Triple("https://m.ebay.de/itm/147226909132", "Irgendwas | eBay", PageKind.NORMAL),
                Triple("https://www.ebay.co.uk/mye/myebay/summary", "My eBay | eBay", PageKind.NORMAL),
                Triple(
                    "https://www.ebay.co.uk/",
                    "Electronics, Cars, Fashion, Collectibles & More | eBay",
                    PageKind.NORMAL,
                ),
                // A seller's title that happens to contain wall words is still an item page.
                Triple(
                    "https://www.ebay.co.uk/itm/127820098965",
                    "Security Measure lock box | eBay UK",
                    PageKind.NORMAL,
                ),
                Triple(
                    "https://www.ebay.co.uk/itm/127820098965",
                    "Pardon Our Interruption vinyl LP | eBay UK",
                    PageKind.NORMAL,
                ),
                Triple("https://ebay.us/m/100axl", "", PageKind.NORMAL),
            ),
        )
    }

    @Test
    fun signIn() {
        assertKinds(
            listOf(
                Triple(
                    "https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn" +
                        "&ru=https%3A%2F%2Fwww.ebay.co.uk%2Fitm%2F127820098965%3Fcta%3Dplacebid",
                    "Sign in or Register | eBay",
                    PageKind.SIGN_IN,
                ),
                Triple("https://signin.ebay.com/signin/?sgfl=gh", "", PageKind.SIGN_IN),
                Triple(
                    "https://www.ebay.com/ws/eBayISAPI.dll?SignIn&siteid=0&ru=https%3A%2F%2Fwww.ebay.com%2F&sgfl=sm",
                    "",
                    PageKind.SIGN_IN,
                ),
                Triple("https://www.ebay.co.uk/signin/s", "", PageKind.SIGN_IN),
                Triple("https://www.ebay.co.uk/some/page", "Sign in or Register | eBay", PageKind.SIGN_IN),
                // ru= values that mention sign-in do not make a page a sign-in page.
                Triple(
                    "https://www.ebay.co.uk/itm/127820098965" +
                        "?ru=https%3A%2F%2Fsignin.ebay.co.uk%2Fws%2FeBayISAPI.dll%3FSignIn",
                    "x | eBay UK",
                    PageKind.NORMAL,
                ),
            ),
        )
    }

    @Test
    fun automationWalls() {
        assertKinds(
            listOf(
                Triple(
                    "https://www.ebay.com/splashui/challenge?ap=1&appName=orch&ru=https%3A%2F%2Fwww.ebay.com%2Fitm%2F1",
                    "Pardon Our Interruption...",
                    PageKind.CHALLENGE,
                ),
                Triple(
                    "https://www.ebay.co.uk/splashui/challenge?ap=1&appName=orch" +
                        "&ru=https%3A%2F%2Fm.ebay.co.uk%2Fitm%2F1&iid=abc&iim=1&iia=2&iiz=3",
                    "",
                    PageKind.CHALLENGE,
                ),
                Triple("https://www.ebay.co.uk/itm/127820098965", "Pardon Our Interruption...", PageKind.CHALLENGE),
                Triple("https://www.ebay.com/splashui/captcha?ap=1&appName=orch", "Security Measure", PageKind.CAPTCHA),
                Triple("https://www.ebay.com/itm/127622190770", "Security Measure", PageKind.CAPTCHA),
                Triple("https://www.ebay.com/splashui/distil?ap=1&appName=orch&page=block&iid=x", "", PageKind.CAPTCHA),
                Triple("https://www.ebay.com/splashui/newcheck?ap=1", "", PageKind.OTHER),
                Triple("https://www.ebay.co.uk/itm/127820098965", "Error Page | eBay", PageKind.RATE_WALL),
                Triple("https://www.ebay.co.uk/sch/i.html?_nkw=x", "Error Page", PageKind.RATE_WALL),
                Triple("https://www.ebay.co.uk/itm/127820098965", "Access Denied", PageKind.RATE_WALL),
            ),
        )
    }

    @Test
    fun thirdPartySignIn() {
        assertKinds(
            listOf(
                Triple(
                    "https://accounts.google.com/o/oauth2/v2/auth?client_id=x&redirect_uri=y",
                    "Sign in - Google Accounts",
                    PageKind.THIRD_PARTY_SIGN_IN,
                ),
                Triple("https://accounts.google.com/v3/signin/identifier?continue=x", "", PageKind.THIRD_PARTY_SIGN_IN),
                Triple(
                    "https://www.facebook.com/login.php?skip_api_login=1",
                    "Log in to Facebook",
                    PageKind.THIRD_PARTY_SIGN_IN,
                ),
                Triple("https://m.facebook.com/v19.0/dialog/oauth?client_id=1", "", PageKind.THIRD_PARTY_SIGN_IN),
                Triple("https://facebook.com/dialog/oauth", "", PageKind.THIRD_PARTY_SIGN_IN),
                Triple("https://appleid.apple.com/auth/authorize?client_id=x", "", PageKind.THIRD_PARTY_SIGN_IN),
            ),
        )
    }

    @Test
    fun goneListingsAndUnknownPages() {
        assertKinds(
            listOf(
                Triple(
                    "https://www.ebay.co.uk/p/404139576?iid=127820098965",
                    "Britain after Rome | eBay",
                    PageKind.NOT_FOUND,
                ),
                Triple("https://www.ebay.com/n/error?statuscode=404", "", PageKind.NOT_FOUND),
                Triple("https://accounts.ebay.com/acctxs/verify?x=1", "Verify it's you", PageKind.OTHER),
                Triple("https://example.com/", "Example", PageKind.OTHER),
                Triple("https://www.ebay.co.uk.example.com/itm/127820098965", "x | eBay UK", PageKind.OTHER),
                Triple("about:blank", "", PageKind.OTHER),
                Triple("chrome-error://chromewebdata/", "", PageKind.OTHER),
                Triple("", "", PageKind.OTHER),
            ),
        )
    }
}
