package app.kestrel.web

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A suspend API over one WebView. Every WebView call runs on [mainDispatcher], the thread that created the view.
 * The driver never calls onPause() or pauseTimers(): either would mark the page hidden (onPause) or freeze every
 * WebView in the process (pauseTimers), and the headless page must keep running with the screen off.
 */
class WebViewDriver internal constructor(
    private val webView: WebView,
    private val mainDispatcher: CoroutineDispatcher,
    // The view's size in pixels. Tests replace it: Robolectric's WebView cannot be laid out.
    private val viewSize: () -> Pair<Int, Int> = { webView.width to webView.height },
) : PageDriver {
    constructor(webView: WebView) : this(webView, Dispatchers.Main.immediate)

    @Volatile
    override var failure: DriverFailure? = null
        private set

    @Volatile
    override var currentUrl: String? = null
        private set

    private val recentDiagnostics = ArrayDeque<String>()
    override val diagnostics: List<String> get() = synchronized(recentDiagnostics) { recentDiagnostics.toList() }

    private val dialogs = mutableListOf<String>()
    override val javaScriptDialogs: List<String> get() = synchronized(dialogs) { dialogs.toList() }

    @Volatile
    override var javaScriptDialogCount: Int = 0
        private set

    private val pendingScripts: MutableSet<CancellableContinuation<String>> =
        Collections.synchronizedSet(
            mutableSetOf(),
        )
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentLoad: LoadTracker? = null

    @Volatile
    private var destroyed = false

    init {
        webView.webViewClient = EbayWebViewClient(Events())
        webView.webChromeClient = HeadlessChromeClient()
    }

    override suspend fun load(
        url: String,
        timeoutMillis: Long,
    ): LoadResult =
        withContext(mainDispatcher) {
            ensureUsable()
            val verdict = NavigationPolicy.decide(url, isMainFrame = true)
            if (verdict.decision != NavigationDecision.ALLOW) {
                return@withContext LoadResult(
                    url,
                    url,
                    "",
                    emptyList(),
                    LoadOutcome.Blocked(url, verdict.decision, verdict.reason),
                )
            }
            val tracker = LoadTracker(url)
            currentLoad = tracker
            webView.loadUrl(url)
            val end = withTimeoutOrNull(timeoutMillis) { tracker.end.await() }
            if (currentLoad === tracker) currentLoad = null
            resultOf(tracker, end)
        }

    private suspend fun resultOf(
        tracker: LoadTracker,
        end: LoadEnd?,
    ): LoadResult {
        val url = tracker.requestedUrl
        return when (end) {
            null -> {
                // Slow subresources can hold back onPageFinished; a document that is already usable counts as loaded.
                val readyState = readyStateOrNull()
                if (readyState == "interactive" || readyState == "complete") {
                    LoadResult(
                        url,
                        webView.url ?: url,
                        webView.title.orEmpty(),
                        tracker.redirects,
                        LoadOutcome.Loaded(null, readyState),
                    )
                } else {
                    webView.stopLoading()
                    LoadResult(
                        url,
                        webView.url ?: url,
                        webView.title.orEmpty(),
                        tracker.redirects,
                        LoadOutcome.TimedOut,
                    )
                }
            }

            is LoadEnd.Finished -> {
                val readyState = awaitParsedDocument()
                LoadResult(url, end.url, end.title, tracker.redirects, LoadOutcome.Loaded(end.httpStatus, readyState))
            }

            is LoadEnd.NetworkError -> {
                LoadResult(url, end.url, "", tracker.redirects, LoadOutcome.NetworkError(end.code, end.description))
            }

            is LoadEnd.Blocked -> {
                LoadResult(
                    url,
                    end.url,
                    "",
                    tracker.redirects,
                    LoadOutcome.Blocked(end.url, end.verdict.decision, end.verdict.reason),
                )
            }

            LoadEnd.RendererGone -> {
                LoadResult(url, url, "", tracker.redirects, LoadOutcome.RendererGone)
            }
        }
    }

    /** onPageFinished can come before the parser is done on some pages; wait briefly for readyState to move on. */
    private suspend fun awaitParsedDocument(): String? {
        var readyState = readyStateOrNull()
        if (readyState != "loading") return readyState
        val parsed =
            waitUntil(PARSE_WAIT_MILLIS, PARSE_POLL_MILLIS) {
                readyState = readyStateOrNull()
                readyState?.takeIf { it != "loading" }
            }
        return parsed ?: readyState
    }

    private suspend fun readyStateOrNull(): String? =
        try {
            evaluate("document.readyState", READY_STATE_TIMEOUT_MILLIS)
        } catch (problem: WebPageException) {
            null
        }

    override suspend fun evaluate(
        script: String,
        timeoutMillis: Long,
    ): String =
        withContext(mainDispatcher) {
            ensureUsable()
            val result =
                withTimeoutOrNull(timeoutMillis) {
                    suspendCancellableCoroutine { continuation ->
                        pendingScripts += continuation
                        continuation.invokeOnCancellation { pendingScripts -= continuation }
                        webView.evaluateJavascript(script) { raw ->
                            if (pendingScripts.remove(continuation) && continuation.isActive) {
                                continuation.resume(ScriptResult.decode(raw))
                            }
                        }
                    }
                }
            result ?: throw ScriptTimeoutException("A page script did not answer within $timeoutMillis ms")
        }

    override suspend fun tap(
        xCssPx: Double,
        yCssPx: Double,
        devicePixelRatio: Double,
        pressMillis: Long,
    ) {
        withContext(mainDispatcher) {
            ensureUsable()
            val (width, height) = viewSize()
            val point = TapGeometry.toViewPixels(xCssPx, yCssPx, devicePixelRatio, width, height)
            val downTime = SystemClock.uptimeMillis()
            var lifted = false
            dispatchTouch(MotionEvent.ACTION_DOWN, downTime, downTime, point)
            try {
                delay(pressMillis)
                ensureUsable()
                dispatchTouch(MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), point)
                lifted = true
            } finally {
                // A finger that went down must come up or be cancelled, or the page sees a press that never ends.
                if (!lifted && failure == null) {
                    withContext(NonCancellable) {
                        dispatchTouch(MotionEvent.ACTION_CANCEL, downTime, SystemClock.uptimeMillis(), point)
                    }
                }
            }
        }
    }

    private fun dispatchTouch(
        action: Int,
        downTime: Long,
        eventTime: Long,
        point: ViewPoint,
    ) {
        val properties =
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        val coordinates =
            MotionEvent.PointerCoords().apply {
                x = point.x
                y = point.y
                pressure = if (action == MotionEvent.ACTION_DOWN) 1f else 0f
                size = 1f
            }
        val event =
            MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                1,
                arrayOf(properties),
                arrayOf(coordinates),
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        try {
            webView.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    override fun destroy() {
        if (Looper.myLooper() == Looper.getMainLooper()) destroyNow() else mainHandler.post { destroyNow() }
    }

    private fun destroyNow() {
        if (destroyed) return
        destroyed = true
        // A page whose renderer died keeps that reason; it is only marked closed if it was still working.
        if (failure == null) markFailed(DriverFailure.Destroyed)
        webView.stopLoading()
        webView.destroy()
    }

    private fun ensureUsable() {
        failure?.let { throw PageUnavailableException(it.description) }
    }

    private fun markFailed(reason: DriverFailure) {
        failure = reason
        currentLoad?.rendererGone()
        val waiting = synchronized(pendingScripts) { pendingScripts.toList().also { pendingScripts.clear() } }
        waiting.forEach { continuation ->
            if (continuation.isActive) continuation.resumeWithException(PageUnavailableException(reason.description))
        }
    }

    private fun remember(text: String) {
        synchronized(recentDiagnostics) {
            recentDiagnostics.addLast(text.take(DIAGNOSTIC_LIMIT))
            while (recentDiagnostics.size > DIAGNOSTICS_KEPT) recentDiagnostics.removeFirst()
        }
    }

    private inner class Events : WebViewEvents {
        override fun pageStarted(url: String) {
            currentUrl = url
            currentLoad?.pageStarted(url)
        }

        override fun pageFinished(
            url: String,
            title: String?,
        ) {
            currentUrl = url
            currentLoad?.pageFinished(url, title)
        }

        override fun redirected(url: String) {
            currentLoad?.redirected(url)
        }

        override fun navigationRefused(
            url: String,
            verdict: NavigationVerdict,
            isMainFrame: Boolean,
        ) {
            remember("refused ${if (isMainFrame) "page" else "frame"} ${url.take(200)}: ${verdict.reason}")
            if (isMainFrame) currentLoad?.navigationRefused(url, verdict)
        }

        override fun mainFrameError(
            url: String,
            code: Int,
            description: String,
        ) {
            remember("load error $code $description")
            currentLoad?.mainFrameError(url, code, description)
        }

        override fun mainFrameHttpError(
            url: String,
            status: Int,
        ) {
            remember("HTTP $status for ${url.take(200)}")
            currentLoad?.mainFrameHttpError(status)
        }

        override fun rendererGone(crashed: Boolean) {
            remember(if (crashed) "renderer crashed" else "renderer stopped by the system")
            markFailed(DriverFailure.RendererGone(crashed))
        }
    }

    /**
     * Nobody sees a headless page. A JavaScript dialog must still be answered or the page stops, so it gets the answer
     * that agrees to nothing (cancel; an alert can only be dismissed) and is kept in [javaScriptDialogs], which the
     * bid page hands to the user instead of going on. Permission prompts are refused.
     */
    private inner class HeadlessChromeClient : WebChromeClient() {
        private fun keep(
            kind: String,
            message: String?,
        ) {
            val text = "$kind: ${message.orEmpty()}".take(DIAGNOSTIC_LIMIT)
            synchronized(dialogs) {
                dialogs += text
                while (dialogs.size > DIAGNOSTICS_KEPT) dialogs.removeAt(0)
                javaScriptDialogCount += 1
            }
            remember("$text (dismissed)")
        }

        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            keep("alert", message)
            result.cancel()
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            keep("confirm", message)
            result.cancel()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            keep("prompt", message)
            result.cancel()
            return true
        }

        override fun onJsBeforeUnload(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            result.confirm()
            return true
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                remember(
                    "console: ${consoleMessage.message()} (${consoleMessage.sourceId().substringBefore(
                        '?',
                    )}:${consoleMessage.lineNumber()})",
                )
            }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback,
        ) {
            callback.invoke(origin, false, false)
        }
    }

    private companion object {
        const val PARSE_WAIT_MILLIS = 3_000L
        const val PARSE_POLL_MILLIS = 100L
        const val READY_STATE_TIMEOUT_MILLIS = 2_000L
        const val DIAGNOSTIC_LIMIT = 300
        const val DIAGNOSTICS_KEPT = 30
    }
}
