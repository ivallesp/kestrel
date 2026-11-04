package app.kestrel.ui.screens.onboarding

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderLink
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * The first launch: Destination.Onboarding. It ends with navigator.completeOnboarding().
 *
 * Placeholder: the Onboarding screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun OnboardingDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "Welcome",
        title = "Kestrel",
        detail = "The first-launch introduction.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = null,
        links = listOf(PlaceholderLink("Finish onboarding") { navigator.completeOnboarding() }),
    )
}
