package app.kestrel.web

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EbayWebViewFactoryTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun assertEbaySettings(webView: WebView) {
        val settings = webView.settings
        assertTrue(settings.javaScriptEnabled)
        assertTrue(settings.domStorageEnabled)
        assertFalse(settings.allowFileAccess)
        assertFalse(settings.allowContentAccess)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.mixedContentMode)
        assertFalse(settings.supportMultipleWindows())
        assertFalse(settings.safeBrowsingEnabled)
        assertEquals(
            "the user agent is the WebView's own",
            WebView(context).settings.userAgentString,
            settings.userAgentString,
        )
        assertTrue(CookieManager.getInstance().acceptCookie())
    }

    private fun request(url: String) =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame() = true

            override fun isRedirect() = false

            override fun hasGesture() = true

            override fun getMethod() = "GET"

            override fun getRequestHeaders() = emptyMap<String, String>()
        }

    @Test
    fun `the headless WebView is configured and never attached`() {
        val webView = EbayWebViewFactory.createHeadless(context)
        assertEbaySettings(webView)
        assertFalse(webView.isAttachedToWindow)
        assertFalse(shadowOf(webView).wasOnPauseCalled())
    }

    // Robolectric's WebView cannot be laid out (its stand-in renderer ignores the frame), so the layout step is
    // checked on a plain view.
    @Test
    fun `the headless layout gives a detached view the phone's screen size`() {
        val view = FrameLayout(context)
        EbayWebViewFactory.layOutHeadless(view)
        assertEquals(1080, view.width)
        assertEquals(2424, view.height)
        assertFalse(view.isAttachedToWindow)
    }

    @Test
    fun `a visible WebView gets the same settings and rules and reports to its host`() {
        val webView = WebView(context)
        val pages = mutableListOf<String>()
        val blocked = mutableListOf<String>()
        val thirdParty = mutableListOf<String>()
        var rendererGone = false
        EbayWebViewFactory.configureVisible(
            webView,
            object : VisibleWebViewListener {
                override fun onPageFinished(
                    url: String,
                    title: String,
                ) {
                    pages += url
                }

                override fun onNavigationBlocked(
                    url: String,
                    reason: String,
                ) {
                    blocked += url
                }

                override fun onThirdPartySignIn(url: String) {
                    thirdParty += url
                }

                override fun onRendererGone(crashed: Boolean) {
                    rendererGone = true
                }
            },
        )
        assertEbaySettings(webView)
        val client = shadowOf(webView).webViewClient
        assertFalse(
            client.shouldOverrideUrlLoading(webView, request("https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn")),
        )
        assertTrue(client.shouldOverrideUrlLoading(webView, request("https://accounts.google.com/o/oauth2/v2/auth")))
        assertTrue(client.shouldOverrideUrlLoading(webView, request("https://www.facebook.com/login.php")))
        assertTrue(client.shouldOverrideUrlLoading(webView, request("intent://x#Intent;end")))
        assertEquals(
            listOf("https://accounts.google.com/o/oauth2/v2/auth", "https://www.facebook.com/login.php"),
            thirdParty,
        )
        assertEquals(listOf("intent://x#Intent;end"), blocked)
        client.onPageFinished(webView, "https://www.ebay.co.uk/")
        assertEquals(listOf("https://www.ebay.co.uk/"), pages)
        assertTrue(
            client.onRenderProcessGone(
                webView,
                mockk<RenderProcessGoneDetail> {
                    every { didCrash() } returns
                        false
                },
            ),
        )
        assertTrue(rendererGone)
    }

    @Test
    fun `a page that fails to load reaches the host, unless Kestrel stopped it itself`() {
        val webView = WebView(context)
        val failed = mutableListOf<String>()
        EbayWebViewFactory.configureVisible(
            webView,
            object : VisibleWebViewListener {
                override fun onMainFrameError(url: String) {
                    failed += url
                }
            },
        )
        val client = shadowOf(webView).webViewClient
        val unreachable =
            mockk<WebResourceError> {
                every { errorCode } returns WebViewClient.ERROR_HOST_LOOKUP
                every { description } returns "net::ERR_NAME_NOT_RESOLVED"
            }
        val signIn = "https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn"
        client.onReceivedError(webView, request(signIn), unreachable)
        // A POST to Google that Kestrel stopped as it started: its error is Kestrel's own doing.
        val google = "https://accounts.google.com/o/oauth2/v2/auth"
        client.onPageStarted(webView, google, null)
        client.onReceivedError(webView, request(google), unreachable)

        assertEquals(listOf(signIn), failed)
    }

    @Test
    fun `a listener that only cares about some events can skip the rest`() {
        val webView = WebView(context)
        EbayWebViewFactory.configureVisible(webView, object : VisibleWebViewListener {})
        assertTrue(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request("market://details?id=x")))
    }
}
