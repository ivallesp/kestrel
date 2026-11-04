package app.kestrel.ebay

import app.kestrel.model.PageKind

/**
 * Decides what the WebView is showing from its URL and title (docs/ARCHITECTURE.md section 3, bidflow AB1, AB4,
 * AB6). Implements [PageClassifier]. Interstitial titles are matched exactly, because an item page's title is
 * the seller's free text and must never be mistaken for a wall.
 */
internal object PageKinds {
    fun classify(
        url: String,
        title: String,
    ): PageKind {
        val parts = UrlParts.parse(url) ?: return PageKind.OTHER
        val host = parts.host
        if (isThirdPartySignIn(host)) return PageKind.THIRD_PARTY_SIGN_IN
        if (!EbayDomains.isEbayHost(host)) return PageKind.OTHER
        val path = parts.path.lowercase()
        kindFromUrl(host, path, parts)?.let { return it }
        kindFromTitle(title)?.let { return it }
        if (path.startsWith("/p/") || path.startsWith("/n/error")) return PageKind.NOT_FOUND
        return PageKind.NORMAL
    }

    private fun kindFromUrl(
        host: String,
        path: String,
        parts: UrlParts,
    ): PageKind? =
        when {
            host.startsWith("signin.") -> PageKind.SIGN_IN

            path.startsWith("/splashui/captcha") -> PageKind.CAPTCHA

            path.startsWith("/splashui/challenge") -> PageKind.CHALLENGE

            // distil?page=block is a block page that may hold a human check: show it to the user.
            path.startsWith("/splashui/distil") -> PageKind.CAPTCHA

            path.startsWith("/splashui/") -> PageKind.OTHER

            path.startsWith("/signin") -> PageKind.SIGN_IN

            path.startsWith("/ws/ebayisapi.dll") && isSignInCommand(parts) -> PageKind.SIGN_IN

            // Account security and identity checks ("verify it's you") live on accounts.ebay.*.
            host.startsWith("accounts.") -> PageKind.OTHER

            else -> null
        }

    /** /ws/eBayISAPI.dll?SignIn&ru=... or ?MfcISAPICommand=SignIn; ru= values are never inspected. */
    private fun isSignInCommand(parts: UrlParts): Boolean {
        val firstName =
            parts.query
                .firstOrNull()
                ?.name
                .orEmpty()
        val command = parts.parameter("MfcISAPICommand").orEmpty()
        return firstName.startsWith("SignIn", ignoreCase = true) || command.startsWith("SignIn", ignoreCase = true)
    }

    private fun kindFromTitle(title: String): PageKind? {
        val normalized =
            title
                .trim()
                .trimEnd('.', '…')
                .trim()
                .lowercase()
        return INTERSTITIAL_TITLES[normalized]
    }

    private fun isThirdPartySignIn(host: String): Boolean =
        host == "accounts.google.com" ||
            host == "facebook.com" ||
            host.endsWith(".facebook.com") ||
            // Sign in with Apple, offered next to Google and Facebook on eBay's sign-in page.
            host == "appleid.apple.com"

    private val INTERSTITIAL_TITLES =
        mapOf(
            "security measure" to PageKind.CAPTCHA,
            "pardon our interruption" to PageKind.CHALLENGE,
            "error page | ebay" to PageKind.RATE_WALL,
            "error page" to PageKind.RATE_WALL,
            // The edge's own 403 page (eBay is served through Akamai, bidflow AB2/AB3): back off like the rate wall.
            "access denied" to PageKind.RATE_WALL,
            "sign in or register | ebay" to PageKind.SIGN_IN,
            "sign in | ebay" to PageKind.SIGN_IN,
        )
}
