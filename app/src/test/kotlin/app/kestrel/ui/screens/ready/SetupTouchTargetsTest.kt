package app.kestrel.ui.screens.ready

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.data.ThemeMode
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.screens.handoff.HandOffPage
import app.kestrel.ui.screens.handoff.HandOffScreen
import app.kestrel.ui.screens.handoff.HandOffUiState
import app.kestrel.ui.screens.onboarding.NoOnboardingActions
import app.kestrel.ui.screens.onboarding.OnboardingScreen
import app.kestrel.ui.screens.onboarding.OnboardingStep
import app.kestrel.ui.screens.onboarding.OnboardingUiState
import app.kestrel.ui.screens.settings.NoSettingsActions
import app.kestrel.ui.screens.settings.SettingsActions
import app.kestrel.ui.screens.settings.SettingsFixtures
import app.kestrel.ui.screens.settings.SettingsScreen
import app.kestrel.ui.screens.signin.SignInScreen
import app.kestrel.ui.screens.signinbrowser.SignInBrowserScreen
import app.kestrel.ui.screens.signinbrowser.SignInBrowserUiState
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Every clickable on the setup screens is at least 48 dp square (DESIGN Q13), and the main buttons do what they say. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class SetupTouchTargetsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { KestrelTheme(darkTheme = false, reduceMotion = true, content = content) }
        composeRule.waitForIdle()
    }

    private fun assertEveryClickableIsAtLeast48Dp() {
        val minimum = with(composeRule.density) { 48.dp.toPx() } - 0.5f
        val nodes = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("no clickables found", nodes.isNotEmpty())
        nodes.forEach { node ->
            // Rows below the fold of a scrolling page are clipped to nothing on screen; their own size still counts.
            val touch = node.touchBoundsInRoot
            val width = maxOf(touch.width, node.size.width.toFloat())
            val height = maxOf(touch.height, node.size.height.toFloat())
            assertTrue("${node.config} is $width x $height px", width >= minimum && height >= minimum)
        }
    }

    @Test
    fun readyToSnipe() {
        val actions = mutableListOf<CheckAction>()
        var checks = 0
        show {
            ReadyScreen(
                state = ReadyFixtures.state(),
                onBack = {},
                onCheckAgain = { checks++ },
                onAction = { actions += it },
                windowInsets = PhoneInsets,
            )
        }
        assertEveryClickableIsAtLeast48Dp()
        composeRule.onNode(hasText("Fix") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Turn on") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Check again") and hasClickAction()).performClick()
        assertEquals(listOf("Fix", "Turn on"), actions.map { it.label })
        assertEquals(1, checks)
    }

    @Test
    fun signInIntroduction() {
        var continued = 0
        show { SignInScreen(onExit = {}, onContinue = { continued++ }, onNotNow = {}, windowInsets = PhoneInsets) }
        assertEveryClickableIsAtLeast48Dp()
        composeRule.onNodeWithText("Continue to eBay").performClick()
        composeRule.onNodeWithContentDescription("Close").assertExists()
        assertEquals(1, continued)
    }

    @Test
    fun signInPage() {
        show {
            SignInBrowserScreen(
                state =
                    SignInBrowserUiState(
                        "signin.ebay.co.uk",
                        0.5f,
                        sheet = null,
                        pageGeneration = 0,
                        signedIn = false,
                    ),
                onClose = {},
                onSheetAction = {},
                onSheetDismiss = {},
                windowInsets = PhoneInsets,
                page = { modifier -> Box(modifier.background(Color.White)) },
            )
        }
        assertEveryClickableIsAtLeast48Dp()
    }

    @Test
    fun handOff() {
        var done = 0
        val moment = CatalogueFixture.canonCaptcha
        show {
            HandOffScreen(
                state =
                    HandOffUiState(
                        now = moment.now,
                        snipe = moment.snipe,
                        status = moment.status,
                        gone = false,
                        page = HandOffPage("www.ebay.co.uk", progress = null, sheet = null, generation = 0),
                    ),
                onClose = {},
                onDone = { done++ },
                onSheetAction = {},
                onSheetDismiss = {},
                windowInsets = PhoneInsets,
                page = { modifier -> Box(modifier.background(Color.White)) },
            )
        }
        assertEveryClickableIsAtLeast48Dp()
        composeRule.onNode(hasText("Done") and hasClickAction()).performClick()
        assertEquals(1, done)
    }

    @Test
    fun settings() {
        val chosen = mutableListOf<String>()
        val actions =
            object : SettingsActions by NoSettingsActions {
                override fun askToSignOut() {
                    chosen += "sign out"
                }

                override fun setDefaultLead(seconds: Int) {
                    chosen += "lead $seconds"
                }

                override fun setTheme(mode: ThemeMode) {
                    chosen += "theme $mode"
                }

                override fun setHaptics(enabled: Boolean) {
                    chosen += "haptics $enabled"
                }
            }
        show { SettingsScreen(state = SettingsFixtures.signedIn, actions = actions, windowInsets = PhoneInsets) }
        assertEveryClickableIsAtLeast48Dp()
        composeRule.onNode(hasText("Sign out") and hasClickAction()).performClick()
        composeRule.onNode(hasText(secondsText(10)) and hasClickAction()).performClick()
        composeRule.onNode(hasText("Dark") and hasClickAction()).performScrollTo().performClick()
        composeRule.onNode(hasText("Haptics") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("sign out", "lead 10", "theme DARK", "haptics false"), chosen)
    }

    @Test
    fun onboarding() {
        var step by mutableStateOf(OnboardingStep.LastSeconds)
        show {
            OnboardingScreen(
                OnboardingUiState(step, finished = false),
                NoOnboardingActions,
                windowInsets = PhoneInsets,
            )
        }
        OnboardingStep.entries.forEach { next ->
            step = next
            composeRule.waitForIdle()
            assertEveryClickableIsAtLeast48Dp()
        }
    }
}
