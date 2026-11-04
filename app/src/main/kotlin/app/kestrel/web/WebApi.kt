package app.kestrel.web

import android.content.Context
import app.kestrel.engine.BidPage
import app.kestrel.engine.BidPageFactory
import app.kestrel.model.Listing
import app.kestrel.model.PageKind
import app.kestrel.model.ParsedLink

// Public API of the WebView layer. Owned by the web lane; bodies are stubs until it lands.

/** Creates headless WebView-backed bid pages. Must be called on the main thread's dispatcher. */
class WebBidPageFactory(
    private val context: Context,
) : BidPageFactory {
    override suspend fun create(): BidPage = TODO("web lane")
}

/** Loads a listing for the add-snipe preview: resolves short links and parses the item page. */
class ListingFetcher(
    private val context: Context,
) {
    suspend fun fetch(link: ParsedLink): FetchResult = TODO("web lane")
}

sealed interface FetchResult {
    data class Loaded(
        val listing: Listing,
    ) : FetchResult

    /** The link is a fixed-price listing or not a listing. */
    data class NotAnAuction(
        val listing: Listing?,
    ) : FetchResult

    /** eBay showed a page that needs the user (sign-in, captcha) or blocked the request. */
    data class Blocked(
        val kind: PageKind,
        val url: String,
    ) : FetchResult

    data class Failed(
        val reason: String,
    ) : FetchResult
}

/** The signed-in state of the shared WebView cookie store. */
object EbaySession {
    /** True when eBay session cookies exist for [host] (cheap, no network). */
    fun hasCookies(host: String): Boolean = TODO("web lane")

    /** Writes cookies to disk now (call after sign-in and after each snipe). */
    suspend fun flush(): Unit = TODO("web lane")

    /** Removes all eBay cookies (sign out). */
    suspend fun signOut(): Unit = TODO("web lane")
}
