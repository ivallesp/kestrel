package app.kestrel.ui.screens.ready

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * S5, the readiness checks: Destination.Ready.
 *
 * Placeholder: the Ready to snipe (S5) screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun ReadyDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "S5 · Ready to snipe",
        title = "Ready to snipe",
        detail = "The checks that let Kestrel bid on time.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = navigator::back,
    )
}
