package app.kestrel.ui.screens.add

import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.web.EbayWebViewFactory
import app.kestrel.web.VisibleWebViewListener

/**
 * Kestrel's own browser on a listing, for S2's "View listing" and "Open on eBay" (DESIGN S2 item 1): the same
 * WebView configuration as sign-in ([EbayWebViewFactory.configureVisible]), so the page shows with the user's eBay
 * session and only eBay pages open. The top bar always names the current host, with a flat progress bar while a
 * page loads. Back closes it.
 */
@Composable
fun ListingBrowser(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    var host by remember(url) { mutableStateOf(hostOf(url)) }
    var progress by remember(url) { mutableIntStateOf(0) }
    var notice by remember(url) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onClose)
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { SymbolIcon(Symbols.Close, contentDescription = "Close") }
                Spacer(Modifier.width(8.dp))
                SymbolIcon(Symbols.Lock, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = host,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (progress < FULL_PROGRESS) {
                    LinearProgressIndicator(
                        progress = { progress / FULL_PROGRESS.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        drawStopIndicator = {},
                    )
                }
            }
            notice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    WebView(context).also { webView ->
                        EbayWebViewFactory.configureVisible(
                            webView,
                            object : VisibleWebViewListener {
                                override fun onPageFinished(
                                    url: String,
                                    title: String,
                                ) {
                                    host = hostOf(url)
                                }

                                override fun onNavigationBlocked(
                                    url: String,
                                    reason: String,
                                ) {
                                    notice = "Kestrel opens only eBay pages here."
                                }

                                override fun onThirdPartySignIn(url: String) {
                                    notice =
                                        "Google sign-in doesn’t work inside apps. Use your eBay email or username and password."
                                }
                            },
                        )
                        webView.webChromeClient = ProgressClient { progress = it }
                        webView.loadUrl(url)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}

/** The factory's plain chrome client, plus the page's load progress for the bar. */
private class ProgressClient(
    private val onProgress: (Int) -> Unit,
) : WebChromeClient() {
    override fun onProgressChanged(
        view: WebView,
        newProgress: Int,
    ) {
        onProgress(newProgress)
    }
}

private const val FULL_PROGRESS = 100

private fun hostOf(url: String): String = Uri.parse(url).host ?: url
