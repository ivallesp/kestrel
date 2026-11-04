package app.kestrel.ui.screens.handoff

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * A visible browser on snipe [id]'s listing, for a captcha or a verification Kestrel hands to the user:
 * Destination.HandOff.
 *
 * Placeholder: the hand-off screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun HandOffDestination(
    id: Long,
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "Hand-off",
        title = "eBay page",
        detail = "Snipe $id’s listing, for a check only you can do.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = navigator::back,
    )
}
