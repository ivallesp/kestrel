package app.kestrel.ui.screens.add

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * S2, a new snipe: Destination.Add([link] is the shared or pasted text, or null).
 *
 * Placeholder: the Add snipe (S2) screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun AddDestination(
    link: String?,
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "S2 · Add snipe",
        title = "New snipe",
        detail = if (link == null) "Opened without a link." else "Opened with “$link”.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = navigator::back,
    )
}
