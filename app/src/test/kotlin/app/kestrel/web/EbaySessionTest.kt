package app.kestrel.web

import android.webkit.CookieManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EbaySessionTest {
    private val cookies get() = CookieManager.getInstance()

    @Before
    fun clearCookies() {
        cookies.removeAllCookies(null)
    }

    @Test
    fun `guest cookies do not count as a session`() {
        cookies.setCookie("https://www.ebay.co.uk", "dp1=bbl/GB; Domain=.ebay.co.uk")
        cookies.setCookie("https://www.ebay.co.uk", "nonsession=BAQAAA; Domain=.ebay.co.uk")
        cookies.setCookie("https://www.ebay.co.uk", "s=CgAD4ACBp; Domain=.ebay.co.uk")
        assertFalse(EbaySession.hasCookies("www.ebay.co.uk"))
    }

    @Test
    fun `a signed-in session cookie counts`() {
        cookies.setCookie("https://www.ebay.co.uk", "dp1=bbl/GB; Domain=.ebay.co.uk")
        cookies.setCookie("https://www.ebay.co.uk", "ds2=sotr/b8_5az10; Domain=.ebay.co.uk")
        assertTrue(EbaySession.hasCookies("www.ebay.co.uk"))
        assertTrue(SessionCookies.containsSignedInCookie("a=1; cid=abc%23123; b=2"))
        assertFalse(SessionCookies.containsSignedInCookie("acid=1; scid=2"))
        assertFalse(SessionCookies.containsSignedInCookie(null))
    }

    @Test
    fun `only eBay hosts are asked`() {
        cookies.setCookie("https://www.example.com", "cid=abc")
        assertFalse(EbaySession.hasCookies("www.example.com"))
        assertFalse(EbaySession.hasCookies("not a host"))
    }

    @Test
    fun `sign out removes the session and flush writes the store`() =
        runTest {
            cookies.setCookie("https://www.ebay.co.uk", "cid=abc%23123; Domain=.ebay.co.uk")
            assertTrue(EbaySession.hasCookies("www.ebay.co.uk"))
            EbaySession.flush()
            EbaySession.signOut()
            assertFalse(EbaySession.hasCookies("www.ebay.co.uk"))
            assertNull(cookies.getCookie("https://www.ebay.co.uk"))
        }
}
