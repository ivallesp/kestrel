package app.kestrel.web

import app.kestrel.model.EbaySite
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What an eBay WebView may do with a navigation. */
enum class NavigationDecision {
    ALLOW,
    BLOCK,

    /** Google, Facebook or Apple sign-in: these refuse embedded WebViews, so the user needs an eBay password. */
    THIRD_PARTY_SIGN_IN,
}

data class NavigationVerdict(
    val decision: NavigationDecision,
    val reason: String,
)

/**
 * Which URLs the eBay WebViews may open. The main frame stays on https eBay hosts (the marketplaces, their sign-in
 * hosts and the share-link hosts). Everything else is refused, including app links such as intent://, ebay:// and
 * market://, which would hand the page to another app. Frames inside an eBay page may load any https page, as in a
 * browser: eBay's own fraud and sign-in checks run in third-party frames.
 */
object NavigationPolicy {
    private val ebayDomains: Set<String> =
        EbaySite.entries.map { it.host.removePrefix("www.") }.toSet() + setOf("ebay.us", "ebay.io")

    private val thirdPartySignInHosts = setOf("accounts.google.com", "appleid.apple.com")
    private val thirdPartySignInDomains = setOf("facebook.com")

    private val schemePattern = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

    fun decide(
        url: String,
        isMainFrame: Boolean,
    ): NavigationVerdict {
        val scheme =
            schemePattern
                .find(url.trim())
                ?.groupValues
                ?.get(1)
                ?.lowercase()
                ?: return block("no scheme")
        return if (isMainFrame) decideMainFrame(url.trim(), scheme) else decideFrame(url.trim(), scheme)
    }

    /** True for eBay marketplace hosts and their subdomains (www., m., signin., pages.), and the share-link hosts. */
    fun isEbayHost(host: String): Boolean {
        val bare = host.lowercase().trimEnd('.')
        return ebayDomains.any { domain -> bare == domain || bare.endsWith(".$domain") }
    }

    /** True for eBay's sign-in hosts (signin.ebay.co.uk, signin.ebay.com, ...). */
    fun isEbaySignInHost(host: String): Boolean {
        val bare = host.lowercase().trimEnd('.')
        return bare.startsWith("signin.") && isEbayHost(bare)
    }

    private fun decideMainFrame(
        url: String,
        scheme: String,
    ): NavigationVerdict {
        if (url == "about:blank") return allow("blank page")
        if (scheme != "https") return block("the $scheme: scheme is not allowed")
        val parsed = url.toHttpUrlOrNull() ?: return block("not a valid https URL")
        val host = parsed.host.lowercase().trimEnd('.')
        if (isThirdPartySignIn(host)) return NavigationVerdict(NavigationDecision.THIRD_PARTY_SIGN_IN, "$host sign-in")
        if (!isEbayHost(host)) return block("$host is not an eBay host")
        if (parsed.port != 443) return block("eBay pages use the standard https port")
        return allow(if (isEbaySignInHost(host)) "eBay sign-in" else "eBay")
    }

    private fun decideFrame(
        url: String,
        scheme: String,
    ): NavigationVerdict =
        when (scheme) {
            "https" -> allow("frame")
            "about", "data", "blob" -> allow("frame document")
            else -> block("the $scheme: scheme is not allowed in a frame")
        }

    private fun isThirdPartySignIn(host: String): Boolean =
        host in thirdPartySignInHosts ||
            thirdPartySignInDomains.any { domain -> host == domain || host.endsWith(".$domain") }

    private fun allow(reason: String) = NavigationVerdict(NavigationDecision.ALLOW, reason)

    private fun block(reason: String) = NavigationVerdict(NavigationDecision.BLOCK, reason)
}
