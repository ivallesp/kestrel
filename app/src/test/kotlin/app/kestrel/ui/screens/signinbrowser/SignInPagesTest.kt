package app.kestrel.ui.screens.signinbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

/** The sign-in browser's rules: which page proves the sign-in, how the header reads, which provider was refused. */
class SignInPagesTest {
    /** What evaluateJavascript hands back for a script returning [json]: the JSON encoding of that string. */
    private fun evaluated(json: String): String = "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun theSignInPageIsEbayUksAndReturnsToItsHomePage() {
        val url = URI.create(SignInPages.signInUrl)
        assertEquals("https", url.scheme)
        assertEquals("signin.ebay.co.uk", url.host)
        val returnTo =
            url.rawQuery
                .split('&')
                .single { it.startsWith("ru=") }
                .removePrefix("ru=")
        assertEquals("https://www.ebay.co.uk/", URLDecoder.decode(returnTo, Charsets.UTF_8))
    }

    @Test
    fun aSignedInHeaderGivesTheUserName() {
        val header = SignInPages.parseHeader(evaluated("""{"signedIn":true,"userId":"lot.42","firstName":"Ada"}"""))
        assertEquals(HeaderReading(signedIn = true, userId = "lot.42", firstName = "Ada"), header)
        assertEquals(SignInEvidence.SignedIn("lot.42"), SignInPages.evidence(header, hasSessionCookie = false))
    }

    @Test
    fun theFirstNameStandsInForAMissingUserName() {
        val header = SignInPages.parseHeader(evaluated("""{"signedIn":true,"userId":"","firstName":"Ada"}"""))
        assertEquals(SignInEvidence.SignedIn("Ada"), SignInPages.evidence(header, hasSessionCookie = false))
    }

    @Test
    fun aSignedOutHeaderWinsOverTheCookieHint() {
        val header = SignInPages.parseHeader(evaluated("""{"signedIn":false,"userId":"","firstName":""}"""))
        assertEquals(SignInEvidence.SignedOut, SignInPages.evidence(header, hasSessionCookie = true))
    }

    @Test
    fun withoutAHeaderOnlyTheSessionCookieCounts() {
        assertNull(SignInPages.parseHeader("null"))
        assertNull(SignInPages.parseHeader(null))
        assertNull(SignInPages.parseHeader("not json"))
        assertNull(SignInPages.parseHeader(evaluated("[1, 2]")))
        assertEquals(SignInEvidence.SignedIn(null), SignInPages.evidence(null, hasSessionCookie = true))
        assertEquals(SignInEvidence.Unknown, SignInPages.evidence(null, hasSessionCookie = false))
        val silent = SignInPages.parseHeader(evaluated("""{"signedIn":null,"userId":"","firstName":""}"""))
        assertEquals(SignInEvidence.Unknown, SignInPages.evidence(silent, hasSessionCookie = false))
    }

    @Test
    fun onlyANormalEbayPageCanProveTheSignIn() {
        assertTrue(SignInPages.canProveSignIn("https://www.ebay.co.uk/", "Electronics, Cars, Fashion | eBay"))
        assertFalse(SignInPages.canProveSignIn(SignInPages.signInUrl, "Sign in or Register | eBay"))
        assertFalse(SignInPages.canProveSignIn("https://www.ebay.co.uk/splashui/captcha?ap=1", "Security Measure"))
        assertFalse(SignInPages.canProveSignIn("https://accounts.google.com/o/oauth2", "Sign in"))
    }

    @Test
    fun theHostBarShowsTheHost() {
        assertEquals("signin.ebay.co.uk", SignInPages.hostOf(SignInPages.signInUrl))
        assertEquals("www.ebay.co.uk", SignInPages.hostOf("https://WWW.ebay.co.uk/itm/1"))
        assertNull(SignInPages.hostOf("about:blank"))
        assertNull(SignInPages.hostOf("not a url with spaces"))
        assertNull(SignInPages.hostOf(null))
    }

    @Test
    fun aRefusedSignInNamesItsProvider() {
        assertEquals("Google", SignInPages.providerOf("https://accounts.google.com/v3/signin"))
        assertEquals("Facebook", SignInPages.providerOf("https://m.facebook.com/login"))
        assertEquals("Apple", SignInPages.providerOf("https://appleid.apple.com/auth"))
        assertEquals("That", SignInPages.providerOf("https://example.org/"))
    }

    @Test
    fun theHeaderScriptOnlyReadsTheHeader() {
        val script = SignInPages.headerScript
        assertTrue(script.contains("window.GHpre"))
        listOf("click(", "submit(", ".value =", "location", "fetch(", "XMLHttpRequest").forEach { forbidden ->
            assertFalse(forbidden, script.contains(forbidden))
        }
    }
}
