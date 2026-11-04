package app.kestrel.ui.screens.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * Settings: Destination.Settings.
 *
 * Placeholder: the Settings screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun SettingsDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "Settings",
        title = "Settings",
        detail = "Account, bidding, alerts and appearance.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = navigator::back,
    )
}
