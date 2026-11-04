package app.kestrel.model

/** What kind of page the WebView is on. Decided from the URL and title only. */
enum class PageKind {
    /** A normal eBay page (item page, My eBay, home). */
    NORMAL,

    /** eBay's sign-in page: the session is gone. */
    SIGN_IN,

    /** "Pardon Our Interruption" check that clears itself: wait in place. */
    CHALLENGE,

    /** A real human check ("Security Measure"): hand the page to the user. */
    CAPTCHA,

    /** "Error Page | eBay" request-rate wall: back off. */
    RATE_WALL,

    /** The listing does not exist (or redirected to a catalogue page). */
    NOT_FOUND,

    /** Google or Facebook sign-in, which cannot work inside a WebView. */
    THIRD_PARTY_SIGN_IN,

    /** Anything else (unknown interstitial): treat as "needs you". */
    OTHER,
}

/** The page model's AUTO_REFRESH reply: the live price, bid count and end time. */
data class AutoRefreshState(
    val currentPrice: Money?,
    val bidCount: Int?,
    val endTimeUtcMillis: Long?,
    /** eBay's own suggested delay before the next poll; null once the auction has ended. */
    val refreshInSeconds: Int?,
    /** True when the reply says the timer has expired (empty formattedText, expired label, no refresh state). */
    val ended: Boolean,
)

/** The result of parsing something the user shared or pasted. */
sealed interface ParsedLink {
    data class Item(
        val ref: ItemRef,
    ) : ParsedLink

    /** ebay.us/m/..., ebay.io/m/...: must be resolved by following the redirect in the WebView. */
    data class ShortLink(
        val url: String,
    ) : ParsedLink

    /** An eBay URL that is not a listing (search, store, catalogue /p/ page). */
    data class NotAListing(
        val url: String,
    ) : ParsedLink

    data object NotEbay : ParsedLink
}
