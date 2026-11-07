package app.kestrel.ui.celebration

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotSurface
import app.kestrel.ui.screens.lot.LotFixtures.resultState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** The loss: what it says, its sound, and when a result page plays it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class LossSceneTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fixture = CatalogueFixture

    @Test
    fun aLossSaysPlainlyWhatHappenedAndCostsNothing() {
        val content = LossContent.of(fixture.nintendo64Lost.resultState())
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "19:42", reduceMotion = true) {
                LossScene(content, onDone = {}, windowInsets = PhoneInsets)
            }
        }
        composeRule.onNodeWithText("YOU LOST").assertExists()
        composeRule.onNodeWithText(content.headline).assertExists()
        composeRule.onNodeWithText("Sold for £79.00").assertExists()
        composeRule.onNodeWithText("Your max £65.00").assertExists()
        composeRule
            .onNodeWithText(
                "You pay nothing. Kestrel is ready for the next one.",
            ).performScrollTo()
            .assertExists()
        composeRule.onNodeWithText("See the receipt").assertIsDisplayed()
    }

    @Test
    fun thePreviewExplainsItselfAndShowsNoMoney() {
        var done = 0
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:22", reduceMotion = true) {
                LossScene(LossContent.Preview, onDone = { done++ }, windowInsets = PhoneInsets)
            }
        }
        composeRule.onNodeWithText("PREVIEW").assertExists()
        composeRule.onNodeWithText("This plays the first time you open a loss. Nothing was bid.").assertExists()
        assertTrue(composeRule.onAllNodesWithText("£", substring = true).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(1, done)
    }

    @Test
    fun theLossTrackPlaysAndStopsWhenTheSceneIsSkipped() {
        val sound = RecordingSound()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "19:42") {
                CompositionLocalProvider(LocalCelebrationSound provides { sound }) {
                    LossScene(LossContent.Preview, onDone = {}, windowInsets = PhoneInsets)
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(500L)
        assertEquals(listOf(SceneTrack.Loss), sound.tracks)
        composeRule.onNodeWithContentDescription(LOSS_SCENE_START, substring = true).performClick()
        composeRule.mainClock.advanceTimeBy(100L)
        assertEquals(1, sound.stops)
    }

    @Test
    fun withReducedMotionItOpensOnItsLastFrameInSilence() {
        val sound = RecordingSound()
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "19:42", reduceMotion = true) {
                CompositionLocalProvider(LocalCelebrationSound provides { sound }) {
                    LossScene(LossContent.Preview, onDone = {}, windowInsets = PhoneInsets)
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(sound.tracks.isEmpty())
    }

    @Test
    fun leavingTheSceneLetsGoOfItsSound() {
        val sound = RecordingSound()
        var showing by mutableStateOf(true)
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "19:42") {
                CompositionLocalProvider(LocalCelebrationSound provides { sound }) {
                    if (showing) LossScene(LossContent.Preview, onDone = {}, windowInsets = PhoneInsets)
                }
            }
        }
        composeRule.waitForIdle()
        showing = false
        composeRule.waitForIdle()
        assertEquals(1, sound.releases)
    }

    @Test
    fun aConfirmedLossPlaysTheLossOnItsFirstOpenOnly() {
        var firstOpen by mutableStateOf(true)
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "19:42", reduceMotion = true) {
                CelebratedResult(fixture.nintendo64Lost.resultState(stampSeal = firstOpen), PhoneInsets) {
                    Text("the result page")
                }
            }
        }
        composeRule.onNodeWithText("YOU LOST").assertExists()
        assertTrue(composeRule.onAllNodesWithText("YOU WON").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("See the receipt").performClick()
        composeRule.onNodeWithText("the result page").assertExists()
        assertTrue(composeRule.onAllNodesWithText("YOU LOST").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aLossOpenedForTheFirstTimeWeeksLaterOpensOnItsPage() {
        val late = fixture.nintendo64Lost.let { it.copy(now = it.now + Duration.ofDays(8)) }
        val state = late.resultState(stampSeal = true)
        assertEquals(false, state.mourn)
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "19:42", reduceMotion = true) {
                CelebratedResult(state, PhoneInsets) { Text("the result page") }
            }
        }
        assertTrue(composeRule.onAllNodesWithText("YOU LOST").fetchSemanticsNodes().isEmpty())
    }

    private class RecordingSound : CelebrationSound {
        val tracks = mutableListOf<SceneTrack>()
        var stops = 0
        var releases = 0

        override fun play(track: SceneTrack) {
            tracks += track
        }

        override fun stop() {
            stops++
        }

        override fun release() {
            releases++
        }
    }

    private companion object {
        const val LOSS_SCENE_START = "A kestrel dives at your lot"
    }
}
