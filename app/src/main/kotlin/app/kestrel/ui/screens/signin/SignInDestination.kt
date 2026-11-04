package app.kestrel.ui.screens.signin

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator

/**
 * S6, the sign-in introduction: Destination.SignIn. "Continue to eBay" swaps it for eBay's page, so finishing or
 * closing the page lands where S6 was opened from. S6 holds no state, so it has no ViewModel.
 */
@Composable
fun SignInDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    SignInScreen(
        onExit = navigator::back,
        onContinue = { navigator.replace(Destination.SignInBrowser) },
        onNotNow = navigator::back,
        modifier = modifier,
        exit = SignInExit.Close,
        windowInsets = windowInsets,
    )
}
