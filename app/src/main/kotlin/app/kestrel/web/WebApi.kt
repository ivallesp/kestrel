package app.kestrel.web

import android.content.Context
import android.os.SystemClock
import android.webkit.CookieManager
import app.kestrel.engine.BidPage
import app.kestrel.engine.BidPageFactory
import app.kestrel.engine.EngineClock
import app.kestrel.model.Listing
import app.kestrel.model.PageKind
import app.kestrel.model.ParsedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// Public API of the WebView layer.

/** Creates headless WebView-backed bid pages. Must be called on the main thread's dispatcher. */
class WebBidPageFactory(
    private val context: Context,
) : BidPageFactory {
    private var clock: EngineClock = SystemEngineClock

    /** Uses [clock] (the engine's true time) for listing timestamps and submit timing. */
    constructor(context: Context, clock: EngineClock) : this(context) {
        this.clock = clock
    }

    override suspend fun create(): BidPage = createPage()

    /** A page for dry runs and checks: it cannot submit and cannot be cast to [BidPage]. Close it after use. */
    suspend fun createPreview(): LayerPreviewPage = LayerPreviewPage(createPage())

    private suspend fun createPage(): WebBidPage =
        withContext(Dispatchers.Main.immediate) {
            val script = PageScript.fromAssets(context)
            WebBidPage(WebViewDriver(EbayWebViewFactory.createHeadless(context)), script, EbayParsers.Default, clock)
        }
}

/** Loads a listing for the add-snipe preview: resolves short links and parses the item page. */
class ListingFetcher(
    private val context: Context,
) {
    private var clock: EngineClock = SystemEngineClock

    constructor(context: Context, clock: EngineClock) : this(context) {
        this.clock = clock
    }

    suspend fun fetch(link: ParsedLink): FetchResult =
        withContext(Dispatchers.Main.immediate) {
            val script = PageScript.fromAssets(context)
            val newDriver: suspend () -> PageDriver = { WebViewDriver(EbayWebViewFactory.createHeadless(context)) }
            ListingFetch(newDriver, script, EbayParsers.Default, clock::nowUtcMillis).fetch(link)
        }
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
    /**
     * True when cookies of a signed-in eBay session exist for [host] (cheap, no network). Guest cookies do not
     * count. A hint only: the page's own sign-in flag, read when a page loads, is the real check.
     */
    fun hasCookies(host: String): Boolean {
        if (!NavigationPolicy.isEbayHost(host)) return false
        return SessionCookies.containsSignedInCookie(CookieManager.getInstance().getCookie("https://$host/"))
    }

    /** Writes cookies to disk now (call after sign-in and after each snipe). */
    suspend fun flush() {
        withContext(Dispatchers.IO) { CookieManager.getInstance().flush() }
    }

    /**
     * Removes all eBay cookies (sign out). The app's WebViews only ever open eBay pages, so this clears every cookie
     * in their store.
     */
    suspend fun signOut() {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                CookieManager.getInstance().removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
            }
        }
        flush()
    }
}

/** The system clocks, for callers that do not pass the engine's true time. */
internal object SystemEngineClock : EngineClock {
    override fun nowUtcMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
