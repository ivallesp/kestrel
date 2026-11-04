package app.kestrel.web

import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView

/** What a visible eBay WebView (sign-in screen, a page handed to the user) tells the screen that hosts it. */
interface VisibleWebViewListener {
    fun onPageFinished(
        url: String,
        title: String,
    ) {}

    /** Kestrel refused a navigation: not an eBay https page, or an app link such as intent://. */
    fun onNavigationBlocked(
        url: String,
        reason: String,
    ) {}

    /** Google, Facebook or Apple sign-in, which refuse WebViews: tell the user to sign in with an eBay password. */
    fun onThirdPartySignIn(url: String) {}

    /** The renderer is gone: the host must remove this WebView and create a new one. */
    fun onRendererGone(crashed: Boolean) {}

    /**
     * The page itself could not load (no route to eBay, a timeout, a reset or TLS error); the WebView shows
     * Chromium's own error page. Never sent for a navigation Kestrel refused or stopped.
     */
    fun onMainFrameError(url: String) {}
}

/** Builds the app's eBay WebViews. All share the app's one WebView profile, so they share the eBay cookies. */
object EbayWebViewFactory {
    /** Pixel 9a screen size. A WebView that is never attached to a window has no size until it is laid out. */
    const val HEADLESS_WIDTH_PX = 1080
    const val HEADLESS_HEIGHT_PX = 2424

    /**
     * A WebView for snipes and checks. Never attach it to a window: Chromium treats a WebView that was never attached
     * as visible, so its timers are not throttled with the screen off, while attaching and detaching it later would
     * mark it hidden. Wrap it in a [WebViewDriver]. Main thread only.
     */
    fun createHeadless(context: Context): WebView {
        val webView = WebView(context.applicationContext)
        applySettings(webView)
        layOutHeadless(webView)
        return webView
    }

    /** Gives a view that is never attached the phone's screen size, as a window would. */
    internal fun layOutHeadless(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(HEADLESS_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEADLESS_HEIGHT_PX, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, HEADLESS_WIDTH_PX, HEADLESS_HEIGHT_PX)
    }

    /** Gives a WebView the UI hosts (sign-in, a page handed to the user) the same settings and navigation rules. */
    fun configureVisible(
        webView: WebView,
        listener: VisibleWebViewListener,
    ) {
        applySettings(webView)
        webView.webViewClient = EbayWebViewClient(VisibleEvents(listener))
        webView.webChromeClient = WebChromeClient()
    }

    internal fun applySettings(webView: WebView) {
        // The user agent is left as the WebView's default (ARCHITECTURE section 1, rule 3).
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(false)
            // Safe Browsing sends page addresses to Google; only eBay pages are ever opened here (rule 5).
            safeBrowsingEnabled = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
    }

    private class VisibleEvents(
        private val listener: VisibleWebViewListener,
    ) : WebViewEvents {
        /** The last main-frame address Kestrel refused, whose load error is Kestrel's own doing. */
        private var refusedUrl: String? = null

        override fun pageStarted(url: String) = Unit

        override fun pageFinished(
            url: String,
            title: String?,
        ) = listener.onPageFinished(url, title.orEmpty())

        override fun redirected(url: String) = Unit

        override fun navigationRefused(
            url: String,
            verdict: NavigationVerdict,
            isMainFrame: Boolean,
        ) {
            if (!isMainFrame) return
            refusedUrl = url
            if (verdict.decision == NavigationDecision.THIRD_PARTY_SIGN_IN) {
                listener.onThirdPartySignIn(url)
            } else {
                listener.onNavigationBlocked(url, verdict.reason)
            }
        }

        override fun mainFrameError(
            url: String,
            code: Int,
            description: String,
        ) {
            if (url != refusedUrl) listener.onMainFrameError(url)
        }

        override fun mainFrameHttpError(
            url: String,
            status: Int,
        ) = Unit

        override fun rendererGone(crashed: Boolean) = listener.onRendererGone(crashed)
    }
}
