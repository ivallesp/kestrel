package app.kestrel.ui.screens.signinbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import app.kestrel.ui.screens.signin.SignInHost
import org.junit.Test

/**
 * eBay's sign-in page inside Kestrel (DESIGN S6, sign-in page). Tests never load eBay, so a blank white page
 * stands in for the WebView, as it looks while eBay's page loads.
 */
class SignInBrowserScreenshots : ScreenshotTest() {
    @Composable
    private fun Browser(state: SignInBrowserUiState) {
        SignInBrowserScreen(
            state = state,
            onClose = {},
            onSheetAction = {},
            onSheetDismiss = {},
            windowInsets = PhoneInsets,
            page = { modifier -> Box(modifier.background(Color.White)) },
        )
    }

    private fun state(
        sheet: BrowserSheet? = null,
        progress: Float? = 0.45f,
    ) = SignInBrowserUiState(
        host = SignInHost,
        progress = progress,
        sheet = sheet,
        pageGeneration = 0,
        signedIn = sheet is BrowserSheet.SignedIn,
    )

    @Test
    fun loadingLight() = capture("S6-browser", darkTheme = false, time = "18:05") { Browser(state()) }

    @Test
    fun loadingDark() = capture("S6-browser", darkTheme = true, time = "18:05") { Browser(state()) }

    @Test
    fun signedInLight() =
        capture("S6-browser-signed-in", darkTheme = false, time = "18:06", wholeScreen = true) {
            Browser(state(BrowserSheet.SignedIn("lot.42"), progress = null))
        }

    @Test
    fun signedInDark() =
        capture("S6-browser-signed-in", darkTheme = true, time = "18:06", wholeScreen = true) {
            Browser(state(BrowserSheet.SignedIn("lot.42"), progress = null))
        }

    @Test
    fun googleLight() =
        capture("S6-browser-google", darkTheme = false, time = "18:05", wholeScreen = true) {
            Browser(state(BrowserSheet.ThirdParty("Google"), progress = null))
        }

    @Test
    fun offlineLight() =
        capture("S6-browser-offline", darkTheme = false, time = "18:05", wholeScreen = true) {
            Browser(state(BrowserSheet.Offline, progress = null))
        }

    @Test
    fun pageStoppedLight() =
        capture("S6-browser-page-stopped", darkTheme = false, time = "18:05", wholeScreen = true) {
            Browser(state(BrowserSheet.PageStopped, progress = null))
        }
}
