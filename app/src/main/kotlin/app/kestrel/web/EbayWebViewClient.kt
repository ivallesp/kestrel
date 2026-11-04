package app.kestrel.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/** What the eBay WebViews report to their owner. Called on the WebView's thread. */
internal interface WebViewEvents {
    fun pageStarted(url: String)

    fun pageFinished(
        url: String,
        title: String?,
    )

    fun redirected(url: String)

    fun navigationRefused(
        url: String,
        verdict: NavigationVerdict,
        isMainFrame: Boolean,
    )

    fun mainFrameError(
        url: String,
        code: Int,
        description: String,
    )

    fun mainFrameHttpError(
        url: String,
        status: Int,
    )

    fun rendererGone(crashed: Boolean)
}

/**
 * The navigation rules of [NavigationPolicy], applied to both the headless and the visible WebView.
 * Returning true from onRenderProcessGone keeps the app alive when the renderer dies; the owner then drops the
 * WebView, which can never be used again.
 */
internal class EbayWebViewClient(
    private val events: WebViewEvents,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url.toString()
        val verdict = NavigationPolicy.decide(url, request.isForMainFrame)
        if (verdict.decision == NavigationDecision.ALLOW) {
            if (request.isForMainFrame && request.isRedirect) events.redirected(url)
            return false
        }
        events.navigationRefused(url, verdict, request.isForMainFrame)
        return true
    }

    // shouldOverrideUrlLoading is not called for POST navigations, so a main frame that starts somewhere it may
    // not go is stopped here.
    override fun onPageStarted(
        view: WebView,
        url: String?,
        favicon: Bitmap?,
    ) {
        val address = url ?: return
        val verdict = NavigationPolicy.decide(address, isMainFrame = true)
        if (verdict.decision != NavigationDecision.ALLOW) {
            view.stopLoading()
            events.navigationRefused(address, verdict, isMainFrame = true)
            return
        }
        events.pageStarted(address)
    }

    override fun onPageFinished(
        view: WebView,
        url: String?,
    ) {
        events.pageFinished(url ?: view.url.orEmpty(), view.title)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            events.mainFrameError(request.url.toString(), error.errorCode, error.description?.toString().orEmpty())
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame) events.mainFrameHttpError(request.url.toString(), errorResponse.statusCode)
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        if (error.url ==
            view.url
        ) {
            events.mainFrameError(
                error.url,
                ERROR_FAILED_SSL_HANDSHAKE,
                "TLS certificate error ${error.primaryError}",
            )
        }
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        events.rendererGone(detail.didCrash())
        return true
    }
}
