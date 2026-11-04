package app.kestrel.ui.screens.signinbrowser

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * S6, eBay's sign-in page in Kestrel's own browser: Destination.SignInBrowser.
 *
 * Placeholder: the sign-in page (S6) screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun SignInBrowserDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "S6 · Sign-in page",
        title = "eBay sign-in",
        detail = "eBay’s own page, shown inside Kestrel.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = navigator::back,
    )
}
