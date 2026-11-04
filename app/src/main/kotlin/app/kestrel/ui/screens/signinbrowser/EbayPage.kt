package app.kestrel.ui.screens.signinbrowser

import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.kestrel.web.EbayWebViewFactory
import app.kestrel.web.VisibleWebViewListener

/** What a visible eBay page tells its screen: the web layer's events plus the load progress. */
interface EbayPageListener : VisibleWebViewListener {
    /** 0 to 100 while a page loads, with the address the WebView shows now. */
    fun onProgress(
        progress: Int,
        url: String?,
    ) {}
}

/**
 * The screen's handle on its visible page, for back navigation and for reading eBay's header. Main thread only;
 * every call is a no-op while no page is attached.
 */
@Stable
class EbayPageController {
    internal var webView: WebView? = null

    fun canGoBack(): Boolean = webView?.canGoBack() == true

    /** The address and the title of the page shown now, or null without a page. */
    fun currentUrl(): String? = webView?.url

    fun currentTitle(): String? = webView?.title

    fun goBack() {
        webView?.goBack()
    }

    /** Runs [script] in the page; [onResult] gets the JSON-encoded value, or null without a page. */
    fun evaluate(
        script: String,
        onResult: (String?) -> Unit,
    ) {
        val page = webView ?: return onResult(null)
        page.evaluateJavascript(script) { onResult(it) }
    }
}

/**
 * A visible eBay page (sign-in, or a listing handed to the user), built with the web layer's visible configuration
 * ([EbayWebViewFactory.configureVisible]): its settings and its navigation rules, which keep the main frame on https
 * eBay pages and refuse Google, Facebook and Apple sign-in. A new [generation] replaces the WebView, as the web layer
 * asks after its renderer is gone. The page pauses with the screen; JavaScript timers of other WebViews (the snipes'
 * own pages) are never touched.
 */
@Composable
internal fun EbayPage(
    url: String,
    generation: Int,
    controller: EbayPageController,
    listener: EbayPageListener,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    key(generation) {
        AndroidView(
            factory = { context ->
                WebView(context).also { webView ->
                    EbayWebViewFactory.configureVisible(webView, listener)
                    // configureVisible installs a plain WebChromeClient; this one only adds the progress the bar shows.
                    webView.webChromeClient = ProgressChromeClient(listener)
                    controller.webView = webView
                    webView.loadUrl(url)
                }
            },
            onRelease = { webView ->
                if (controller.webView === webView) controller.webView = null
                webView.destroy()
            },
            modifier = modifier,
        )
    }
    DisposableEffect(lifecycle, controller) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> controller.webView?.onPause()
                    Lifecycle.Event.ON_RESUME -> controller.webView?.onResume()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** A plain WebChromeClient that reports progress. */
private class ProgressChromeClient(
    private val listener: EbayPageListener,
) : WebChromeClient() {
    override fun onProgressChanged(
        view: WebView,
        newProgress: Int,
    ) {
        listener.onProgress(newProgress, view.url)
    }
}
