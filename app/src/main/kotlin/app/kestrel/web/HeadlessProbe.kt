package app.kestrel.web

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener

/**
 * What a never-attached WebView can do on this phone, measured on a local page (no network, no eBay): animation
 * frames, intersection callbacks, a CSS transition, timers, and whether a dispatched touch reaches the page as a
 * trusted event. eBay's bid layer opens with a transition, so a hidden page without frames may never show it.
 */
object HeadlessProbe {
    private const val LOAD_TIMEOUT_MILLIS = 5_000L
    private const val SETTLE_MILLIS = 300L
    private const val PRESS_MILLIS = 60L
    private const val MEASURE_MILLIS = 2_300L
    private const val TARGET_CSS_PX = 100.0

    suspend fun run(context: Context): String =
        withContext(Dispatchers.Main) {
            val webView = EbayWebViewFactory.createHeadless(context)
            try {
                val loaded = CompletableDeferred<Unit>()
                webView.webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            loaded.complete(Unit)
                        }
                    }
                webView.loadDataWithBaseURL("https://probe.invalid/", PAGE, "text/html", "utf-8", null)
                withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { loaded.await() }
                    ?: return@withContext "the probe page did not load"
                delay(SETTLE_MILLIS)
                val ratio = evaluate(webView, "String(window.devicePixelRatio || 1)")?.toDoubleOrNull() ?: 1.0
                tap(webView, (TARGET_CSS_PX * ratio).toFloat(), (TARGET_CSS_PX * ratio).toFloat())
                delay(MEASURE_MILLIS)
                evaluate(webView, "JSON.stringify(window.__probe)") ?: "the probe page did not answer"
            } catch (error: Exception) {
                "the probe failed: ${error.message}"
            } finally {
                webView.destroy()
            }
        }

    private suspend fun tap(
        webView: WebView,
        x: Float,
        y: Float,
    ) {
        val downTime = SystemClock.uptimeMillis()
        dispatch(webView, MotionEvent.ACTION_DOWN, downTime, downTime, x, y)
        delay(PRESS_MILLIS)
        dispatch(webView, MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), x, y)
    }

    private fun dispatch(
        webView: WebView,
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            webView.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private suspend fun evaluate(
        webView: WebView,
        script: String,
    ): String? {
        val answer = CompletableDeferred<String?>()
        webView.evaluateJavascript(script) { answer.complete(it) }
        val raw = withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { answer.await() } ?: return null
        // evaluateJavascript hands back the JSON encoding of the value: decode the string.
        return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
    }

    private val PAGE =
        """
        <!doctype html><html><head><meta name="viewport" content="width=device-width">
        <style>
          body { margin: 0 }
          #target { position: absolute; left: 50px; top: 50px; width: 100px; height: 100px }
          #box { width: 40px; height: 40px; opacity: 0; transition: opacity 0.2s linear }
          #box.on { opacity: 1 }
        </style></head><body><div id="target"></div><div id="box"></div>
        <script>
          window.__probe = { frames: 0, intersections: 0, transitionEnd: false, opacityAfter: null, timers: 0,
            touches: [], clicks: [], hidden: document.hidden, visibility: document.visibilityState,
            viewport: [window.innerWidth, window.innerHeight] };
          var started = Date.now();
          function frame() { __probe.frames++; if (Date.now() - started < 2000) requestAnimationFrame(frame); }
          requestAnimationFrame(frame);
          new IntersectionObserver(function () { __probe.intersections++; }).observe(document.body);
          var box = document.getElementById('box');
          box.addEventListener('transitionend', function () { __probe.transitionEnd = true; });
          setTimeout(function () { box.classList.add('on'); }, 50);
          setTimeout(function () { __probe.opacityAfter = getComputedStyle(box).opacity; }, 1500);
          var timer = setInterval(function () { __probe.timers++; }, 100);
          setTimeout(function () { clearInterval(timer); }, 2000);
          document.addEventListener('touchstart', function (e) {
            __probe.touches.push({ trusted: e.isTrusted, x: e.touches[0] ? Math.round(e.touches[0].clientX) : null });
          }, true);
          document.addEventListener('click', function (e) {
            __probe.clicks.push({ trusted: e.isTrusted, x: Math.round(e.clientX), y: Math.round(e.clientY) });
          }, true);
        </script></body></html>
        """.trimIndent()
}
