package app.kestrel.web

import android.net.Uri
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WebViewDriverTest {
    private lateinit var webView: WebView
    private val item = "https://www.ebay.co.uk/itm/127820098965"

    @Before
    fun setUp() {
        webView = EbayWebViewFactory.createHeadless(RuntimeEnvironment.getApplication())
    }

    // Robolectric's WebView cannot be laid out, so the driver is told the size the phone gives it.
    private fun TestScope.driver() =
        WebViewDriver(webView, UnconfinedTestDispatcher(testScheduler)) {
            EbayWebViewFactory.HEADLESS_WIDTH_PX to EbayWebViewFactory.HEADLESS_HEIGHT_PX
        }

    private val client: WebViewClient get() = shadowOf(webView).webViewClient

    private fun request(
        url: String,
        mainFrame: Boolean = true,
        redirect: Boolean = false,
    ) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)

        override fun isForMainFrame() = mainFrame

        override fun isRedirect() = redirect

        override fun hasGesture() = false

        override fun getMethod() = "GET"

        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    private fun answerScript(rawValue: String) =
        shadowOf(webView).lastEvaluatedJavascriptCallback.onReceiveValue(rawValue)

    @Test
    fun `the installed client keeps the main frame on eBay and refuses app links`() =
        runTest {
            val driver = driver()
            assertFalse(client.shouldOverrideUrlLoading(webView, request(item)))
            assertFalse(
                client.shouldOverrideUrlLoading(webView, request("https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn")),
            )
            assertTrue(client.shouldOverrideUrlLoading(webView, request("intent://item#Intent;scheme=ebay;end")))
            assertTrue(client.shouldOverrideUrlLoading(webView, request("ebay://link/?nav=item.view&id=1")))
            assertTrue(client.shouldOverrideUrlLoading(webView, request("market://details?id=com.ebay.mobile")))
            assertTrue(client.shouldOverrideUrlLoading(webView, request("https://accounts.google.com/o/oauth2/auth")))
            assertTrue(client.shouldOverrideUrlLoading(webView, request("https://www.example.com/")))
            assertFalse(
                client.shouldOverrideUrlLoading(webView, request("https://h.online-metrix.net/fp", mainFrame = false)),
            )
            assertTrue(client.shouldOverrideUrlLoading(webView, request("intent://x#Intent;end", mainFrame = false)))
            assertTrue(driver.diagnostics.any { it.contains("accounts.google.com") })
        }

    @Test
    fun `load waits for the page to finish, follows redirects and reports the final URL`() =
        runTest {
            val driver = driver()
            val loading = async(UnconfinedTestDispatcher(testScheduler)) { driver.load(item, 30_000) }
            assertEquals(item, shadowOf(webView).lastLoadedUrl)
            val relisted = "https://www.ebay.co.uk/itm/127820098966"
            assertFalse(client.shouldOverrideUrlLoading(webView, request(relisted, redirect = true)))
            client.onPageStarted(webView, relisted, null)
            client.onPageFinished(webView, relisted)
            assertEquals("document.readyState", shadowOf(webView).lastEvaluatedJavascript)
            answerScript("\"complete\"")
            val result = loading.await()
            assertEquals(LoadOutcome.Loaded(null, "complete"), result.outcome)
            assertEquals(relisted, result.finalUrl)
            assertEquals(listOf(relisted), result.redirects)
            assertEquals(relisted, driver.currentUrl)
        }

    @Test
    fun `an HTTP error page still loads, with its status`() =
        runTest {
            val driver = driver()
            val loading = async(UnconfinedTestDispatcher(testScheduler)) { driver.load(item, 30_000) }
            client.onPageStarted(webView, item, null)
            client.onReceivedHttpError(
                webView,
                request(item),
                WebResourceResponse("text/html", "utf-8", 403, "Forbidden", emptyMap(), null),
            )
            client.onPageFinished(webView, item)
            answerScript("\"complete\"")
            assertEquals(LoadOutcome.Loaded(403, "complete"), loading.await().outcome)
        }

    @Test
    fun `a main-frame network error ends the load`() =
        runTest {
            val driver = driver()
            val loading = async(UnconfinedTestDispatcher(testScheduler)) { driver.load(item, 30_000) }
            val error =
                mockk<WebResourceError> {
                    every { errorCode } returns WebViewClient.ERROR_HOST_LOOKUP
                    every { description } returns "net::ERR_NAME_NOT_RESOLVED"
                }
            client.onReceivedError(webView, request("https://h.online-metrix.net/fp", mainFrame = false), error)
            client.onReceivedError(webView, request(item), error)
            assertEquals(
                LoadOutcome.NetworkError(WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED"),
                loading.await().outcome,
            )
        }

    @Test
    fun `a redirect off eBay is refused and ends the load as blocked`() =
        runTest {
            val driver = driver()
            val loading = async(UnconfinedTestDispatcher(testScheduler)) { driver.load(item, 30_000) }
            assertTrue(
                client.shouldOverrideUrlLoading(webView, request("https://www.paypal.com/checkout", redirect = true)),
            )
            val outcome = loading.await().outcome as LoadOutcome.Blocked
            assertEquals(NavigationDecision.BLOCK, outcome.decision)
            assertEquals("https://www.paypal.com/checkout", outcome.url)
        }

    @Test
    fun `a main frame that starts off eBay, as a form post can, is stopped`() =
        runTest {
            val driver = driver()
            val loading = async(UnconfinedTestDispatcher(testScheduler)) { driver.load(item, 30_000) }
            client.onPageStarted(webView, "https://www.example.com/landing", null)
            assertTrue(loading.await().outcome is LoadOutcome.Blocked)
        }

    @Test
    fun `a URL that is not an eBay page is never loaded`() =
        runTest {
            val result = driver().load("http://www.ebay.co.uk/itm/1", 30_000)
            assertTrue(result.outcome is LoadOutcome.Blocked)
            assertNull(shadowOf(webView).lastLoadedUrl)
        }

    @Test
    fun `a load that never finishes times out unless the document is usable`() =
        runTest {
            val driver = driver()
            val loading = async(UnconfinedTestDispatcher(testScheduler)) { driver.load(item, 10_000) }
            advanceTimeBy(10_001)
            answerScript("\"interactive\"")
            assertEquals(LoadOutcome.Loaded(null, "interactive"), loading.await().outcome)
            val stalled = async(UnconfinedTestDispatcher(testScheduler)) { driver.load(item, 10_000) }
            advanceTimeBy(10_001)
            answerScript("\"loading\"")
            assertEquals(LoadOutcome.TimedOut, stalled.await().outcome)
        }

    @Test
    fun `evaluate decodes the JSON string evaluateJavascript returns`() =
        runTest {
            val driver = driver()
            val value =
                async(UnconfinedTestDispatcher(testScheduler)) { driver.evaluate("window.__kestrel.probe()", 5_000) }
            assertEquals("window.__kestrel.probe()", shadowOf(webView).lastEvaluatedJavascript)
            answerScript("\"{\\\"ok\\\":true,\\\"title\\\":\\\"Item | eBay\\\"}\"")
            assertEquals("{\"ok\":true,\"title\":\"Item | eBay\"}", value.await())
        }

    @Test
    fun `a script that never answers times out`() =
        runTest {
            val driver = driver()
            val failure =
                async(
                    UnconfinedTestDispatcher(testScheduler),
                ) { runCatching { driver.evaluate("1", 1_000) }.exceptionOrNull() }
            advanceTimeBy(1_001)
            assertTrue(failure.await() is ScriptTimeoutException)
        }

    @Test
    fun `a dead renderer is handled, fails pending scripts and every later call`() =
        runTest {
            val driver = driver()
            val pending =
                async(
                    UnconfinedTestDispatcher(testScheduler),
                ) { runCatching { driver.evaluate("1", 5_000) }.exceptionOrNull() }
            val loading = async(UnconfinedTestDispatcher(testScheduler)) { driver.load(item, 30_000) }
            val detail = mockk<RenderProcessGoneDetail> { every { didCrash() } returns true }
            assertTrue("returning true keeps the app alive", client.onRenderProcessGone(webView, detail))
            assertTrue(pending.await() is PageUnavailableException)
            assertEquals(LoadOutcome.RendererGone, loading.await().outcome)
            assertEquals(DriverFailure.RendererGone(crashed = true), driver.failure)
            assertTrue(runCatching { driver.evaluate("2", 5_000) }.exceptionOrNull() is PageUnavailableException)
            assertTrue(runCatching { driver.tap(10.0, 10.0, 2.625) }.exceptionOrNull() is PageUnavailableException)
            driver.destroy()
            assertEquals(DriverFailure.RendererGone(crashed = true), driver.failure)
            assertTrue(shadowOf(webView).wasDestroyCalled())
        }

    @Test
    fun `a tap is a finger down and up at the CSS point times the pixel ratio`() =
        runTest {
            val events = mutableListOf<MotionEvent>()
            webView.setOnTouchListener { _, event ->
                events += MotionEvent.obtain(event)
                true
            }
            driver().tap(206.0, 484.0, 2.625, 60)
            assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), events.map { it.actionMasked })
            events.forEach { event ->
                assertEquals(540.75f, event.x, 0.01f)
                assertEquals(1270.5f, event.y, 0.01f)
                assertEquals(MotionEvent.TOOL_TYPE_FINGER, event.getToolType(0))
                assertEquals(InputDevice.SOURCE_TOUCHSCREEN, event.source)
            }
            assertEquals(events[0].downTime, events[1].downTime)
            assertTrue(events[1].eventTime - events[0].eventTime >= 0)
        }

    @Test
    fun `a tap meant for one document is not delivered to the next`() =
        runTest {
            val events = mutableListOf<Int>()
            webView.setOnTouchListener { _, event ->
                events += event.actionMasked
                true
            }
            val driver = driver()
            val before = driver.navigationCount
            client.onPageStarted(webView, item, null)
            assertEquals(before + 1, driver.navigationCount)
            val stopped =
                runCatching {
                    driver.tap(
                        206.0,
                        484.0,
                        2.625,
                        60,
                        onlyInNavigation = before,
                    )
                }.exceptionOrNull()
            assertTrue(stopped is TapAbortedException)
            assertFalse((stopped as TapAbortedException).fingerWentDown)
            assertTrue("no finger went down", events.isEmpty())
            val current = driver.navigationCount
            val tapping =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    runCatching { driver.tap(206.0, 484.0, 2.625, 60, onlyInNavigation = current) }.exceptionOrNull()
                }
            client.onPageStarted(webView, "https://www.ebay.co.uk/itm/127820098966", null)
            advanceTimeBy(61)
            val cancelled = tapping.await() as TapAbortedException
            assertTrue(cancelled.fingerWentDown)
            assertEquals(
                "cancelled, never lifted into a click",
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL),
                events,
            )
        }

    @Test
    fun `a tap outside the WebView is refused and nothing is dispatched`() =
        runTest {
            var touched = false
            webView.setOnTouchListener { _, _ ->
                touched = true
                true
            }
            assertTrue(runCatching { driver().tap(500.0, 10.0, 2.625) }.exceptionOrNull() is IllegalArgumentException)
            assertFalse(touched)
        }

    @Test
    fun `the driver never pauses the WebView, and destroy releases it from any thread`() =
        runTest {
            val driver = driver()
            driver.tap(10.0, 10.0, 2.625)
            val thread = Thread { driver.destroy() }
            thread.start()
            thread.join()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(shadowOf(webView).wasDestroyCalled())
            assertFalse(shadowOf(webView).wasOnPauseCalled())
            assertEquals(DriverFailure.Destroyed, driver.failure)
            assertTrue(runCatching { driver.evaluate("1", 1_000) }.exceptionOrNull() is PageUnavailableException)
        }

    @Test
    fun `JavaScript dialogs on the headless page agree to nothing and are kept for the user`() =
        runTest {
            val driver = driver()
            val chrome = shadowOf(webView).webChromeClient
            val confirm = mockk<android.webkit.JsResult>(relaxed = true)
            assertTrue(chrome.onJsConfirm(webView, item, "Are you sure?", confirm))
            verify { confirm.cancel() }
            verify(exactly = 0) { confirm.confirm() }
            val alert = mockk<android.webkit.JsResult>(relaxed = true)
            assertTrue(chrome.onJsAlert(webView, item, "Please acknowledge these terms", alert))
            verify { alert.cancel() }
            val prompt = mockk<android.webkit.JsPromptResult>(relaxed = true)
            assertTrue(chrome.onJsPrompt(webView, item, "Enter the code", "", prompt))
            verify { prompt.cancel() }
            verify(exactly = 0) { prompt.confirm(any()) }
            assertEquals(3, driver.javaScriptDialogCount)
            assertEquals(
                listOf("confirm: Are you sure?", "alert: Please acknowledge these terms", "prompt: Enter the code"),
                driver.javaScriptDialogs,
            )
            assertTrue(driver.diagnostics.any { it.contains("confirm: Are you sure? (dismissed)") })
        }
}
