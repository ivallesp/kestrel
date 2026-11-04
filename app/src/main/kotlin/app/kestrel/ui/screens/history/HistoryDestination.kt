package app.kestrel.ui.screens.history

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * Every finished snipe: Destination.History.
 *
 * Placeholder: the History screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun HistoryDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "History",
        title = "History",
        detail = "Every finished snipe.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = navigator::back,
    )
}
