package app.kestrel.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.BottomFade
import app.kestrel.ui.components.OutlinedActionButton
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.theme.Spacing

/** A link on a placeholder screen, so every destination can be reached on a phone before its screen exists. */
@Immutable
data class PlaceholderLink(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The stand-in every destination shows until its screen lands: the screen's name and what it was opened with, in the
 * catalogue type, with back and, on Home, links to every other destination.
 */
@Composable
fun PlaceholderScreen(
    overline: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    onBack: (() -> Unit)? = null,
    links: List<PlaceholderLink> = emptyList(),
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(windowInsets)
                    .padding(bottom = Spacing.ScreenEnd),
            ) {
                if (onBack != null) {
                    Box(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
                        IconButton(onClick = onBack) { SymbolIcon(Symbols.ArrowBack, contentDescription = "Back") }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                }
                Column(Modifier.padding(horizontal = Spacing.ColumnA)) {
                    SmallCaps(overline)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (links.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            links.forEach { link ->
                                OutlinedActionButton(
                                    text = link.label,
                                    onClick = link.onClick,
                                    height = Spacing.TouchTarget,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            BottomFade(windowInsets = windowInsets)
        }
    }
}
