package app.kestrel.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
    // Lot and Result read their snipe from the database, where a fresh install has none.
    private val titles =
        listOf(
            Destination.Lot(3) to NOT_ON_THE_LIST,
            Destination.Result(3) to NOT_ON_THE_LIST,
            Destination.Ready to "Ready to snipe",
            Destination.SignIn to "Sign in to eBay",
            // eBay's pages show their host in the bar, and the hand-off its Done button whatever the snipe.
            Destination.SignInBrowser to "signin.ebay.co.uk",
            Destination.HandOff(3) to "Done",
            Destination.Group(1) to "Group",
            Destination.Settings to "Settings",
            Destination.History to "History",
        )

    /** S6 and eBay's pages are left with Close (DESIGN S6); every other screen has Back. */
    private val closedWithClose = setOf(Destination.SignIn, Destination.SignInBrowser, Destination.HandOff(3))

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
            waitForText(title)
            // S5's chip repeats its title once every check passes.
            composeRule.onAllNodesWithText(title).onFirst().assertIsDisplayed()
            val exit = if (destination in closedWithClose) "Close" else "Back"
            composeRule.onNodeWithContentDescription(exit).performClick()
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
        waitForText(NOT_ON_THE_LIST)
        composeRule.onNodeWithText(NOT_ON_THE_LIST).assertIsDisplayed()
    }

    @Test
    fun aMessageLeftOnTheWayOutShowsOnTheScreenUnderneath() {
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                KestrelShell(
                    startStack = listOf(Destination.Home, Destination.SignInBrowser),
                    onFinishOnboarding = {},
                    onExit = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Snipes").assertIsDisplayed()
        waitForText("Sign-in didn’t finish.")
        composeRule.onNodeWithText("Sign-in didn’t finish.").assertIsDisplayed()
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
        // Skip the three pages, then pass on signing in and on notifications: onboarding ends on Ready to snipe,
        // with Home underneath.
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.waitForIdle()
        assertTrue(stored)
        composeRule.onAllNodesWithText("Ready to snipe").onFirst().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Snipes").assertIsDisplayed()
    }

    /** Screens that read the database show their words a moment after they open. */
    private fun waitForText(text: String) {
        composeRule.waitUntil(
            LOAD_TIMEOUT_MILLIS,
        ) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val NOT_ON_THE_LIST = "Not on your list"
        const val LOAD_TIMEOUT_MILLIS = 5_000L
    }
}
