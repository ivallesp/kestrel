package app.kestrel.ui.screens.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelShell
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * S2 in the real app shell, with the app's own container: Add opens on its link entry, and Close returns to the
 * list. Opened without a link, so nothing is loaded.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class AddShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addOpensFromTheListAndClosesBackToIt() {
        val incoming = MutableSharedFlow<Destination>(extraBufferCapacity = 1)
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                KestrelShell(
                    startStack = listOf(Destination.Home),
                    onFinishOnboarding = {},
                    onExit = {},
                    incoming = incoming,
                )
            }
        }
        runBlocking { incoming.emit(Destination.Add(null)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New snipe").assertIsDisplayed()
        composeRule.onNodeWithText("eBay listing link").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Snipes").assertIsDisplayed()
    }
}
