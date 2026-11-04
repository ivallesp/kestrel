package app.kestrel.ui.shell

import androidx.compose.runtime.Composable
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.RecordingNavigator
import app.kestrel.ui.harness.ScreenshotTest
import app.kestrel.ui.screens.add.AddDestination
import app.kestrel.ui.screens.group.GroupDestination
import app.kestrel.ui.screens.handoff.HandOffDestination
import app.kestrel.ui.screens.history.HistoryDestination
import app.kestrel.ui.screens.home.HomeDestination
import app.kestrel.ui.screens.lot.LotDestination
import app.kestrel.ui.screens.onboarding.OnboardingDestination
import app.kestrel.ui.screens.ready.ReadyDestination
import app.kestrel.ui.screens.result.ResultDestination
import app.kestrel.ui.screens.settings.SettingsDestination
import app.kestrel.ui.screens.signin.SignInDestination
import app.kestrel.ui.screens.signinbrowser.SignInBrowserDestination
import org.junit.Test

/** Each destination's placeholder, as the shell shows it until its screen lands, in both themes. */
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
    fun addLight() = light("add") { AddDestination("https://ebay.us/m/AbC123", navigator, windowInsets = PhoneInsets) }

    @Test
    fun addDark() = dark("add") { AddDestination(null, navigator, windowInsets = PhoneInsets) }

    @Test
    fun lotLight() = light("lot") { LotDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun lotDark() = dark("lot") { LotDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun resultLight() = light("result") { ResultDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun resultDark() = dark("result") { ResultDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun readyLight() = light("ready") { ReadyDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun readyDark() = dark("ready") { ReadyDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun signInLight() = light("signin") { SignInDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun signInDark() = dark("signin") { SignInDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun signInBrowserLight() =
        light("signin-browser") { SignInBrowserDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun signInBrowserDark() = dark("signin-browser") { SignInBrowserDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun handOffLight() = light("handoff") { HandOffDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun handOffDark() = dark("handoff") { HandOffDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun groupLight() = light("group") { GroupDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun groupDark() = dark("group") { GroupDestination(1, navigator, windowInsets = PhoneInsets) }

    @Test
    fun settingsLight() = light("settings") { SettingsDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun settingsDark() = dark("settings") { SettingsDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun historyLight() = light("history") { HistoryDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun historyDark() = dark("history") { HistoryDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun onboardingLight() = light("onboarding") { OnboardingDestination(navigator, windowInsets = PhoneInsets) }

    @Test
    fun onboardingDark() = dark("onboarding") { OnboardingDestination(navigator, windowInsets = PhoneInsets) }
}
