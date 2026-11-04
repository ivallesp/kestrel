package app.kestrel.ui.screens.lot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.KestrelApp
import app.kestrel.data.AddResult
import app.kestrel.data.HOUR
import app.kestrel.data.gbp
import app.kestrel.data.testListing
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelShell
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Live Update's "Edit max" opens S3 with the edit sheet (DESIGN 5.1). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class LotEditMaxLinkTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theEditMaxLinkOpensTheLotWithItsEditSheet() {
        val container = ApplicationProvider.getApplicationContext<KestrelApp>().container
        val listing =
            testListing(
                endTimeUtcMillis = System.currentTimeMillis() + 3 * HOUR,
                fetchedAtUtcMillis = System.currentTimeMillis(),
            ).copy(imageUrl = null)
        val snipe =
            runBlocking {
                container.repository.addFromListing(
                    listing,
                    gbp("48.00"),
                    leadSeconds = 6,
                ) as AddResult.Added
            }.snipe

        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                KestrelShell(
                    startStack = listOf(Destination.Home, Destination.Lot(snipe.id, editMax = true)),
                    onFinishOnboarding = {},
                    onExit = {},
                )
            }
        }

        composeRule.waitUntil(LOAD_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Edit your max").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Edit your max").assertIsDisplayed()
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 5_000L
    }
}
