package app.kestrel.ui.shell

import androidx.compose.runtime.Composable
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.RecordingNavigator
import app.kestrel.ui.harness.ScreenshotTest
import app.kestrel.ui.screens.history.HistoryDestination
import app.kestrel.ui.screens.home.HomeDestination
import app.kestrel.ui.screens.lot.LotDestination
import app.kestrel.ui.screens.result.ResultDestination
import org.junit.Test

/**
 * Each destination's placeholder, as the shell shows it until its screen lands, in both themes. Destinations whose
 * screen has landed (Ready, sign-in, eBay's pages, Settings and Onboarding) have their own screenshots instead.
 */
class ShellPlaceholderScreenshots : ScreenshotTest() {
    private val navigator = RecordingNavigator()

    private fun light(
        name: String,
        content: @Composable () -> Unit,
    ) {
        capture("shell-$name", darkTheme = false, time = "18:08", content = content)
    }

    private fun dark(
        name: String,
        content: @Composable () -> Unit,
    ) {
        capture("shell-$name", darkTheme = true, time = "18:08", content = content)
    }

    @Test
    fun homeLight() = light("home") { HomeDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun homeDark() = dark("home") { HomeDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun lotLight() = light("lot") { LotDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun lotDark() = dark("lot") { LotDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun resultLight() = light("result") { ResultDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun resultDark() = dark("result") { ResultDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun historyLight() = light("history") { HistoryDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun historyDark() = dark("history") { HistoryDestination(navigator, windowInsets = PhoneInsets) }
}
