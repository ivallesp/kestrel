package app.kestrel.web

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** One WebView page as the bid page and the listing fetcher use it. Implemented by [WebViewDriver]. */
interface PageDriver {
    /** Set once the page can no longer be used (renderer gone, destroyed). Every call then throws. */
    val failure: DriverFailure?

    /** The main frame's URL as last reported by the WebView. */
    val currentUrl: String?

    /** Recent JavaScript dialogs, console errors and blocked navigations, for diagnostics. */
    val diagnostics: List<String>

    /**
     * How many JavaScript dialogs (alert, confirm, prompt) the page has opened. A headless page has to dismiss them to
     * keep running, so the bid page treats a new one as something for the user.
     */
    val javaScriptDialogCount: Int

    /** The most recent of those dialogs, oldest first. */
    val javaScriptDialogs: List<String>

    suspend fun load(
        url: String,
        timeoutMillis: Long,
    ): LoadResult

    /** Runs [script] in the main frame and returns its result, decoded from evaluateJavascript's JSON. */
    suspend fun evaluate(
        script: String,
        timeoutMillis: Long = DEFAULT_SCRIPT_TIMEOUT_MILLIS,
    ): String

    /** A finger tap (MotionEvent down, then up after [pressMillis]) at a CSS-pixel point of the page. */
    suspend fun tap(
        xCssPx: Double,
        yCssPx: Double,
        devicePixelRatio: Double,
        pressMillis: Long = DEFAULT_PRESS_MILLIS,
    )

    /** Releases the WebView. Safe to call more than once and from any thread. */
    fun destroy()

    companion object {
        const val DEFAULT_SCRIPT_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_PRESS_MILLIS = 60L
    }
}

/**
 * Polls [predicate] every [pollMillis] until it returns a value, the time runs out (null) or the page fails (null).
 * Exceptions from [predicate] propagate.
 */
suspend fun <T : Any> PageDriver.waitUntil(
    timeoutMillis: Long,
    pollMillis: Long,
    predicate: suspend () -> T?,
): T? =
    withTimeoutOrNull(timeoutMillis) {
        var found: T? = null
        while (found == null && failure == null) {
            found = predicate()
            if (found == null) delay(pollMillis)
        }
        found
    }

/** Why a page can no longer be used. */
sealed interface DriverFailure {
    val description: String

    data class RendererGone(
        val crashed: Boolean,
    ) : DriverFailure {
        override val description: String
            get() = if (crashed) "The page's renderer crashed" else "Android stopped the page's renderer to free memory"
    }

    data object Destroyed : DriverFailure {
        override val description: String get() = "The page was closed"
    }
}

data class LoadResult(
    val requestedUrl: String,
    val finalUrl: String,
    val title: String,
    /** Main-frame server redirects, in order. */
    val redirects: List<String>,
    val outcome: LoadOutcome,
)

sealed interface LoadOutcome {
    /**
     * A document loaded. [httpStatus] is only known for HTTP errors (WebView does not report successful statuses);
     * the page is then eBay's error page and is classified like any other.
     */
    data class Loaded(
        val httpStatus: Int?,
        val readyState: String?,
    ) : LoadOutcome

    /** No document: offline, DNS, TLS or connection failure (WebResourceError code and text). */
    data class NetworkError(
        val code: Int,
        val description: String,
    ) : LoadOutcome

    /** Kestrel refused a navigation of the main frame (not eBay, not https, or a third-party sign-in). */
    data class Blocked(
        val url: String,
        val decision: NavigationDecision,
        val reason: String,
    ) : LoadOutcome

    data object RendererGone : LoadOutcome

    data object TimedOut : LoadOutcome
}

open class WebPageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The page's renderer is gone or the page was closed. */
class PageUnavailableException(
    message: String,
) : WebPageException(message)

/** A script did not answer in time. */
class ScriptTimeoutException(
    message: String,
) : WebPageException(message)

/** kestrel.js could not be installed or answered something unreadable. */
class PageScriptException(
    message: String,
) : WebPageException(message)

/** No page could be loaded (network failure or timeout). */
class PageLoadException(
    message: String,
) : WebPageException(message)

/** submit() pressed nothing: the bid was not sent. */
class SubmitNotDispatchedException(
    message: String,
) : WebPageException(message)
