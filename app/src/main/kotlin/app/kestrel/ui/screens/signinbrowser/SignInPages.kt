package app.kestrel.ui.screens.signinbrowser

import androidx.compose.runtime.Immutable
import app.kestrel.ebay.PageClassifier
import app.kestrel.model.EbaySite
import app.kestrel.model.PageKind
import app.kestrel.ui.screens.signin.SignInHost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.URLEncoder

/** What eBay's global header says about the page's user (window.GHpre on www pages). */
@Immutable
data class HeaderReading(
    /** GHpre.userAuth; null when the header did not say. */
    val signedIn: Boolean?,
    /** GHpre.userId, the eBay user name ("lot.42"); blank when signed out. */
    val userId: String?,
    /** GHpre.fn, the first name in the header greeting. */
    val firstName: String?,
)

/** Whether a page proves the user signed in. */
sealed interface SignInEvidence {
    /** Signed in; [name] is the eBay user name when the page gave one. */
    data class SignedIn(
        val name: String?,
    ) : SignInEvidence

    data object SignedOut : SignInEvidence

    /** The page said nothing either way (no header, and no session cookie). */
    data object Unknown : SignInEvidence
}

/** The rules of the sign-in browser, apart from the WebView so they run on the JVM. */
object SignInPages {
    /** eBay UK's sign-in page, returning to the home page, whose header says who is signed in. */
    val signInUrl: String =
        "https://$SignInHost/ws/eBayISAPI.dll?SignIn&ru=" +
            URLEncoder.encode(EbaySite.UK.origin + "/", Charsets.UTF_8)

    /**
     * Reads eBay's global header without touching the page: window.GHpre carries userAuth, userId and fn on every
     * www page (item pages 2025-06 to 2026-04, the same object kestrel.js reads). Returns a JSON string, or null.
     */
    val headerScript: String =
        """
        (function () {
          try {
            var header = window.GHpre;
            if (!header || typeof header !== 'object') return null;
            return JSON.stringify({
              signedIn: typeof header.userAuth === 'boolean' ? header.userAuth : null,
              userId: typeof header.userId === 'string' ? header.userId : '',
              firstName: typeof header.fn === 'string' ? header.fn : ''
            });
          } catch (error) {
            return null;
          }
        })()
        """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses what evaluateJavascript handed back for [headerScript]: the JSON encoding of the script's value, so a
     * string inside a string, or "null". Anything else reads as no header.
     */
    fun parseHeader(result: String?): HeaderReading? =
        try {
            val outer = result?.let { json.parseToJsonElement(it) }
            val text = (outer as? JsonPrimitive)?.takeIf { it.isString }?.content
            val header = text?.let { json.parseToJsonElement(it) } as? JsonObject
            header?.let {
                HeaderReading(
                    signedIn =
                        (it["signedIn"] as? JsonPrimitive)
                            ?.takeUnless { value ->
                                value is JsonNull
                            }?.booleanOrNull,
                    userId = (it["userId"] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null },
                    firstName = (it["firstName"] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null },
                )
            }
        } catch (error: IllegalArgumentException) {
            null
        }

    /**
     * The page's own flag decides; the session cookie is only a hint, used when the page has no header at all.
     * The user name is the one eBay shows ("Signed in as lot.42"); the first name stands in when it is missing.
     */
    fun evidence(
        header: HeaderReading?,
        hasSessionCookie: Boolean,
    ): SignInEvidence =
        when (header?.signedIn) {
            true -> {
                SignInEvidence.SignedIn(header.userId ?: header.firstName)
            }

            false -> {
                SignInEvidence.SignedOut
            }

            null -> {
                if (hasSessionCookie) {
                    SignInEvidence.SignedIn(
                        header?.userId ?: header?.firstName,
                    )
                } else {
                    SignInEvidence.Unknown
                }
            }
        }

    /** Only a normal eBay page (the home page sign-in returns to, My eBay) can prove the user signed in. */
    fun canProveSignIn(
        url: String,
        title: String,
    ): Boolean = PageClassifier.classify(url, title) == PageKind.NORMAL

    /** The host the bar shows ("signin.ebay.co.uk"), or null for a page without one (about:blank). */
    fun hostOf(url: String?): String? =
        try {
            url
                ?.let {
                    URI
                        .create(it)
                        .host
                        ?.lowercase()
                        ?.trimEnd('.')
                }?.ifEmpty { null }
        } catch (error: IllegalArgumentException) {
            null
        }

    /** The provider of a refused third-party sign-in, for "Google sign-in doesn’t work inside apps". */
    fun providerOf(url: String): String {
        val host = hostOf(url).orEmpty()
        return when {
            host == "accounts.google.com" || host.endsWith(".google.com") -> "Google"
            host == "facebook.com" || host.endsWith(".facebook.com") -> "Facebook"
            host.endsWith("apple.com") -> "Apple"
            else -> "That"
        }
    }
}
