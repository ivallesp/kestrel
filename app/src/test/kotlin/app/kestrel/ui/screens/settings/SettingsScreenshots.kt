package app.kestrel.ui.screens.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.kestrel.BuildConfig
import app.kestrel.data.ThemeMode
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import org.junit.Test

/** Settings at 18:08 (DESIGN Settings), signed in as the fixture account with five armed snipes. */
class SettingsScreenshots : ScreenshotTest() {
    @Composable
    private fun Settings(
        state: SettingsUiState,
        scrollState: ScrollState = rememberScrollState(),
    ) {
        SettingsScreen(
            state = state,
            actions = NoSettingsActions,
            windowInsets = PhoneInsets,
            scrollState = scrollState,
        )
    }

    @Composable
    private fun Scrolled(state: SettingsUiState) {
        // Past the end: the scroll clamps to the bottom of the page.
        Settings(state, remember { ScrollState(initial = Int.MAX_VALUE) })
    }

    @Test
    fun settingsLight() = capture("settings", darkTheme = false, time = "18:08") { Settings(SettingsFixtures.signedIn) }

    @Test
    fun settingsDark() = capture("settings", darkTheme = true, time = "18:08") { Settings(SettingsFixtures.signedIn) }

    @Test
    fun settingsLargeText() =
        capture("settings", darkTheme = false, time = "18:08", fontScale = 1.3f) { Settings(SettingsFixtures.signedIn) }

    @Test
    fun scrolledLight() =
        capture("settings-scrolled", darkTheme = false, time = "18:08") {
            Scrolled(SettingsFixtures.signedIn)
        }

    @Test
    fun scrolledDark() =
        capture("settings-scrolled", darkTheme = true, time = "18:08") {
            Scrolled(SettingsFixtures.signedIn)
        }

    @Test
    fun middleLight() =
        capture("settings-middle", darkTheme = false, time = "18:08") {
            Settings(SettingsFixtures.signedIn, remember { ScrollState(initial = MIDDLE_PX) })
        }

    @Test
    fun signedOutLight() =
        capture("settings-signed-out", darkTheme = false, time = "18:08") { Settings(SettingsFixtures.signedOut) }

    @Test
    fun signOutDialogLight() =
        capture("settings-sign-out", darkTheme = false, time = "18:08", wholeScreen = true) {
            Settings(SettingsFixtures.signedIn.copy(confirmingSignOut = true))
        }

    @Test
    fun signOutDialogDark() =
        capture("settings-sign-out", darkTheme = true, time = "18:08", wholeScreen = true) {
            Settings(SettingsFixtures.signedIn.copy(confirmingSignOut = true))
        }

    @Test
    fun howKestrelBidsLight() =
        capture("settings-how", darkTheme = false, time = "18:08", wholeScreen = true) {
            Settings(SettingsFixtures.signedIn.copy(sheet = SettingsSheet.HowKestrelBids))
        }

    @Test
    fun licencesLight() =
        capture("settings-licences", darkTheme = false, time = "18:08", wholeScreen = true) {
            Settings(SettingsFixtures.signedIn.copy(sheet = SettingsSheet.Licences))
        }

    @Test
    fun fontLicenceLight() =
        capture("settings-font-licence", darkTheme = false, time = "18:08", wholeScreen = true) {
            Settings(SettingsFixtures.signedIn.copy(sheet = SettingsSheet.FontLicence))
        }

    private companion object {
        /** About halfway down, where This phone and Appearance sit. */
        const val MIDDLE_PX = 1_500
    }
}

internal object SettingsFixtures {
    private val fixture = CatalogueFixture

    val signedIn: SettingsUiState =
        SettingsUiState(
            now = fixture.now,
            account = AccountState.SignedIn(fixture.ACCOUNT),
            armedSnipes = fixture.snipes.count { it.maxBid != null && it.status.isActiveBid },
            defaultLeadSeconds = 6,
            liveUpdateMinutes = 10,
            themeMode = ThemeMode.SYSTEM,
            pureBlack = false,
            haptics = true,
            readiness = PhoneReadiness(problems = 1, checkedAt = fixture.readinessCheckedAt),
            version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            confirmingSignOut = false,
            sheet = null,
        )

    val signedOut: SettingsUiState
        get() =
            signedIn.copy(
                account = AccountState.SignedOut,
                readiness = PhoneReadiness(problems = 2, checkedAt = fixture.readinessCheckedAt),
            )
}

/** Settings' actions for screenshots: none of them goes anywhere. */
internal object NoSettingsActions : SettingsActions {
    override fun back() = Unit

    override fun signIn() = Unit

    override fun askToSignOut() = Unit

    override fun confirmSignOut() = Unit

    override fun cancelSignOut() = Unit

    override fun setDefaultLead(seconds: Int) = Unit

    override fun setLiveUpdateMinutes(minutes: Int) = Unit

    override fun openNotificationSettings() = Unit

    override fun openReady() = Unit

    override fun setTheme(mode: ThemeMode) = Unit

    override fun setPureBlack(enabled: Boolean) = Unit

    override fun setHaptics(enabled: Boolean) = Unit

    override fun openSheet(sheet: SettingsSheet) = Unit

    override fun closeSheet() = Unit
}
