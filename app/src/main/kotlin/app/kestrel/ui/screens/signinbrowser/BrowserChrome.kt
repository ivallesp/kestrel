package app.kestrel.ui.screens.signinbrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.screens.ready.TopBarHeight
import app.kestrel.ui.theme.KestrelTheme

/**
 * The bar above an eBay page (DESIGN S6, sign-in page): close, then a lock and the page's host in titleSmall,
 * read-only and always the current one, then any [actions]. Under it, a flat LinearProgressIndicator while a page
 * loads ([progress] from 0 to 1), otherwise a hairline.
 */
@Composable
internal fun BrowserBar(
    host: String?,
    progress: Float?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { SymbolIcon(Symbols.Close, contentDescription = "Close") }
            Spacer(Modifier.width(4.dp))
            Row(
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // No page, no address: the lock only ever stands next to a host.
                if (host != null) {
                    SymbolIcon(
                        Symbols.Lock,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 16.dp,
                        contentDescription = "Secure page",
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = host,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
        Box(Modifier.fillMaxWidth().height(ProgressHeight)) {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = KestrelTheme.catalogue.rule,
                    drawStopIndicator = {},
                )
            } else {
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    thickness = 1.dp,
                    color = KestrelTheme.catalogue.rule,
                )
            }
        }
    }
}

private val ProgressHeight = 4.dp
