package app.kestrel.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The app shell shows every destination's screen, goes back, and takes screens asked for from outside. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class ShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    // Onboarding has no back arrow (it is the first launch); finishingOnboardingOpensHome covers it.
    private val titles =
        listOf(
            Destination.Lot(3) to "Lot detail",
            Destination.Result(3) to "Result",
            Destination.Ready to "Ready to snipe",
            Destination.SignIn to "Sign in to eBay",
            Destination.SignInBrowser to "eBay sign-in",
            Destination.HandOff(3) to "eBay page",
            Destination.Group(1) to "Group",
            Destination.Settings to "Settings",
            Destination.History to "History",
        )

    @Test
    fun everyDestinationOpensItsScreenAndBackReturns() {
        val incoming = MutableSharedFlow<Destination>(extraBufferCapacity = 1)
        var exited = false
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                KestrelShell(
                    startStack = listOf(Destination.Home),
                    onFinishOnboarding = {},
                    onExit = { exited = true },
                    incoming = incoming,
                )
            }
        }
        composeRule.onNodeWithText("Snipes").assertIsDisplayed()
        titles.forEach { (destination, title) ->
            runBlocking { incoming.emit(destination) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Back").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Snipes").assertIsDisplayed()
        }
        assertTrue("back from Home leaves nothing to pop", !exited)
    }

    @Test
    fun aRestoredStackStartsOnItsTopScreen() {
        composeRule.setContent {
            KestrelTheme(darkTheme = true, reduceMotion = true) {
                KestrelShell(
                    startStack = listOf(Destination.Home, Destination.Lot(8)),
                    onFinishOnboarding = {},
                    onExit = {},
                )
            }
        }
        composeRule.onNodeWithText("Snipe 8.").assertIsDisplayed()
    }

    @Test
    fun finishingOnboardingOpensHome() {
        var stored = false
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                KestrelShell(
                    startStack = listOf(Destination.Onboarding),
                    onFinishOnboarding = { stored = true },
                    onExit = {},
                )
            }
        }
        composeRule.onNodeWithText("Finish onboarding").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Snipes").assertIsDisplayed()
        assertTrue(stored)
    }
}
