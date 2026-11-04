package app.kestrel.ui.screens.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderLink
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * S1, the list of snipes: Destination.Home.
 *
 * Placeholder: the Home (S1) screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun HomeDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "S1 · Home",
        title = "Snipes",
        detail = "The list of snipes comes with the Home screen. Until then, every screen opens from here.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = null,
        links =
            listOf(
                PlaceholderLink("Add a snipe") { navigator.open(Destination.Add()) },
                PlaceholderLink("Lot detail") { navigator.open(Destination.Lot(SAMPLE_ID)) },
                PlaceholderLink("Result") { navigator.open(Destination.Result(SAMPLE_ID)) },
                PlaceholderLink("Ready to snipe") { navigator.open(Destination.Ready) },
                PlaceholderLink("Sign in") { navigator.open(Destination.SignIn) },
                PlaceholderLink("eBay sign-in page") { navigator.open(Destination.SignInBrowser) },
                PlaceholderLink("eBay page for a check") { navigator.open(Destination.HandOff(SAMPLE_ID)) },
                PlaceholderLink("Group") { navigator.open(Destination.Group(SAMPLE_ID)) },
                PlaceholderLink("Settings") { navigator.open(Destination.Settings) },
                PlaceholderLink("History") { navigator.open(Destination.History) },
                PlaceholderLink("Onboarding") { navigator.open(Destination.Onboarding) },
            ),
    )
}

/** The snipe and group the placeholder links open; the real list opens the ones the user taps. */
private const val SAMPLE_ID = 1L
