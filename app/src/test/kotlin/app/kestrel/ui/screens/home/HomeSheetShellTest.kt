package app.kestrel.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.KestrelApp
import app.kestrel.data.AddResult
import app.kestrel.data.HOUR
import app.kestrel.data.testListing
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelShell
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The list's "Set a max" in the real app shell, over the app's own database: the set sheet opens over the list
 * (DESIGN S1), not the lot's detail. The listing has no photo, so nothing is fetched.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class HomeSheetShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun setAMaxOpensTheSetSheetOverTheList() {
        val container = ApplicationProvider.getApplicationContext<KestrelApp>().container
        val listing =
            testListing(
                title = "Polaroid SX-70 Land Camera",
                endTimeUtcMillis = System.currentTimeMillis() + 50 * HOUR,
                fetchedAtUtcMillis = System.currentTimeMillis(),
            ).copy(imageUrl = null)
        val added = runBlocking { container.repository.addFromListing(listing, maxBid = null, leadSeconds = 6) }
        assertTrue("the watched lot is stored", added is AddResult.Added)

        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                KestrelShell(startStack = listOf(Destination.Home), onFinishOnboarding = {}, onExit = {})
            }
        }
        composeRule.waitUntil(LOAD_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Set a max").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Set a max").performClick()
        composeRule.waitUntil(LOAD_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Set your max").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Set your max").assertIsDisplayed()
        // The list stays underneath: the sheet is not the lot's detail.
        composeRule.onNodeWithText("Snipes").assertExists()
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 5_000L
    }
}
