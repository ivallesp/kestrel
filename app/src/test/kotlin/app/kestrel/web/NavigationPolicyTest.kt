package app.kestrel.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    private fun mainFrame(url: String) = NavigationPolicy.decide(url, isMainFrame = true).decision

    private fun frame(url: String) = NavigationPolicy.decide(url, isMainFrame = false).decision

    @Test
    fun `eBay pages on every supported site are allowed`() {
        listOf(
            "https://www.ebay.co.uk/itm/127820098965",
            "https://www.ebay.com/itm/127622190770?mkcid=16",
            "https://www.ebay.de/itm/147226909132",
            "https://www.ebay.com.au/itm/1",
            "https://m.ebay.co.uk/itm/-/262896136247",
            "https://ebay.co.uk/itm/1",
            "https://pages.ebay.com/help",
            "https://WWW.EBAY.CO.UK/itm/1",
            "https://www.ebay.co.uk./itm/1",
        ).forEach { assertEquals(it, NavigationDecision.ALLOW, mainFrame(it)) }
    }

    @Test
    fun `eBay sign-in hosts and share links are allowed`() {
        assertEquals(NavigationDecision.ALLOW, mainFrame("https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn&ru=x"))
        assertEquals(NavigationDecision.ALLOW, mainFrame("https://signin.ebay.com/ws/eBayISAPI.dll?SignIn"))
        assertEquals(NavigationDecision.ALLOW, mainFrame("https://ebay.us/m/100axl"))
        assertEquals(NavigationDecision.ALLOW, mainFrame("https://ebay.io/m/MLxzM3"))
        assertTrue(NavigationPolicy.isEbaySignInHost("signin.ebay.co.uk"))
        assertFalse(NavigationPolicy.isEbaySignInHost("www.ebay.co.uk"))
        assertFalse(NavigationPolicy.isEbaySignInHost("signin.example.com"))
    }

    @Test
    fun `app links and every non-https scheme are blocked`() {
        listOf(
            "intent://item/1#Intent;scheme=ebay;package=com.ebay.mobile;end",
            "ebay://link/?nav=item.view&id=1",
            "market://details?id=com.ebay.mobile",
            "http://www.ebay.co.uk/itm/1",
            "javascript:alert(1)",
            "file:///sdcard/page.html",
            "content://com.example/page",
            "data:text/html,<p>hi</p>",
            "mailto:someone@example.com",
            "tel:123",
            "about:srcdoc",
            "not a url",
            "",
        ).forEach { assertEquals(it, NavigationDecision.BLOCK, mainFrame(it)) }
    }

    @Test
    fun `hosts that only look like eBay are blocked`() {
        listOf(
            "https://evil-ebay.co.uk/itm/1",
            "https://ebay.co.uk.evil.example/itm/1",
            "https://www.ebay.co.uk@evil.example/itm/1",
            "https://notebay.com/",
            "https://www.ebay.co.uk:8443/itm/1",
            "https://www.paypal.com/checkout",
        ).forEach { assertEquals(it, NavigationDecision.BLOCK, mainFrame(it)) }
    }

    @Test
    fun `Google, Facebook and Apple sign-in are flagged as third-party sign-in`() {
        listOf(
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=x",
            "https://www.facebook.com/login.php",
            "https://m.facebook.com/dialog/oauth",
            "https://facebook.com/v19.0/dialog/oauth",
            "https://appleid.apple.com/auth/authorize",
        ).forEach { assertEquals(it, NavigationDecision.THIRD_PARTY_SIGN_IN, mainFrame(it)) }
    }

    @Test
    fun `frames inside eBay pages may load https pages but not app links`() {
        assertEquals(NavigationDecision.ALLOW, frame("https://h.online-metrix.net/fp/tags.js"))
        assertEquals(NavigationDecision.ALLOW, frame("about:blank"))
        assertEquals(NavigationDecision.ALLOW, frame("blob:https://www.ebay.co.uk/1"))
        assertEquals(NavigationDecision.BLOCK, frame("intent://x#Intent;end"))
        assertEquals(NavigationDecision.BLOCK, frame("market://details?id=x"))
        assertEquals(NavigationDecision.BLOCK, frame("http://www.ebay.co.uk/frame"))
    }

    @Test
    fun `the blank page used between loads is allowed`() {
        assertEquals(NavigationDecision.ALLOW, mainFrame("about:blank"))
    }

    @Test
    fun `every verdict says why`() {
        assertTrue(NavigationPolicy.decide("https://www.paypal.com/", true).reason.contains("www.paypal.com"))
        assertTrue(NavigationPolicy.decide("intent://x", true).reason.contains("intent"))
    }
}
