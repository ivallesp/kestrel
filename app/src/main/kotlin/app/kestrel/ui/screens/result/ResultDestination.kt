package app.kestrel.ui.screens.result

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * S4, one snipe's result and receipt: Destination.Result.
 *
 * Placeholder: the Result (S4) screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun ResultDestination(
    id: Long,
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "S4 · Result",
        title = "Result",
        detail = "Snipe $id.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = navigator::back,
    )
}
