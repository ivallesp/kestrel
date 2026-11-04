package app.kestrel.web

/**
 * Reads a cookie header ("k=v; k2=v2", as CookieManager.getCookie returns it) for signs of a signed-in eBay session.
 * Guests also get eBay cookies (dp1, nonsession, ebay, s), so only cookies seen in a signed-in capture and not on
 * guests count (bid capture 2026-04-30 against guest lists 2026). This is a cheap hint: the page's own
 * GHpre.userAuth is the real check.
 */
internal object SessionCookies {
    private val signedInCookieNames = setOf("cid", "ebaysid", "shs", "ds2")

    fun containsSignedInCookie(cookieHeader: String?): Boolean =
        cookieHeader
            ?.split(';')
            ?.map { it.substringBefore('=').trim() }
            ?.any { it in signedInCookieNames }
            ?: false
}
