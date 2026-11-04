package app.kestrel.ui.screens.signin

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * S6, the sign-in introduction: Destination.SignIn.
 *
 * Placeholder: the Sign-in introduction (S6) screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun SignInDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "S6 · Sign-in",
        title = "Sign in to eBay",
        detail = "What happens when you sign in.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = navigator::back,
    )
}
