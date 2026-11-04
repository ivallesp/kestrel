package app.kestrel.web

import kotlinx.coroutines.CompletableDeferred

/** How one main-frame load ended, as the WebViewClient callbacks reported it. */
internal sealed interface LoadEnd {
    data class Finished(
        val url: String,
        val title: String,
        val httpStatus: Int?,
    ) : LoadEnd

    data class NetworkError(
        val url: String,
        val code: Int,
        val description: String,
    ) : LoadEnd

    data class Blocked(
        val url: String,
        val verdict: NavigationVerdict,
    ) : LoadEnd

    data object RendererGone : LoadEnd
}

/**
 * Follows one main-frame load through the WebViewClient callbacks and completes [end] once: when the page
 * finished, failed, was blocked or lost its renderer. HTTP errors do not end the load, since eBay's error page
 * still loads and is classified by its title. Called on the WebView's thread only.
 */
internal class LoadTracker(
    val requestedUrl: String,
) {
    val end = CompletableDeferred<LoadEnd>()
    private val redirectUrls = mutableListOf<String>()
    private var started = false
    private var httpErrorStatus: Int? = null

    val redirects: List<String> get() = redirectUrls.toList()

    fun pageStarted() {
        started = true
    }

    fun redirected(url: String) {
        redirectUrls += url
    }

    fun pageFinished(
        url: String,
        title: String?,
    ) {
        // A late finish of the previous document must not end this load.
        if (!started && url != requestedUrl && url !in redirectUrls) return
        end.complete(LoadEnd.Finished(url, title.orEmpty(), httpErrorStatus))
    }

    fun mainFrameError(
        url: String,
        code: Int,
        description: String,
    ) {
        end.complete(LoadEnd.NetworkError(url, code, description))
    }

    fun mainFrameHttpError(status: Int) {
        httpErrorStatus = status
    }

    fun navigationRefused(
        url: String,
        verdict: NavigationVerdict,
    ) {
        end.complete(LoadEnd.Blocked(url, verdict))
    }

    fun rendererGone() {
        end.complete(LoadEnd.RendererGone)
    }
}
