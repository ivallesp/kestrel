package app.kestrel.ui.celebration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotSurface
import app.kestrel.ui.screens.lot.LotFixtures.resultState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The celebration over a won result, and its preview from Settings, which never shows an amount or bids. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class WinCelebrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fixture = CatalogueFixture

    @Test
    fun thePreviewExplainsItselfAndShowsNoMoney() {
        var done = 0
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:22", reduceMotion = true) {
                WinCelebration(CelebrationContent.Preview, onDone = { done++ }, windowInsets = PhoneInsets)
            }
        }
        // Small capitals are set in capitals.
        for (words in listOf(
            "PREVIEW",
            "You won",
            "Your next win",
            "This plays the first time you open a win. Nothing was bid.",
        )) {
            composeRule.onNodeWithText(words).assertExists()
        }
        composeRule.onNodeWithText(COFFEE_NOTE).performScrollTo().assertExists()
        assertTrue(composeRule.onAllNodesWithText("£", substring = true).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(1, done)
    }

    @Test
    fun theCoffeeButtonIsAPlaceholderThatSaysSo() {
        assertEquals(null, CoffeeLink.url)
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:22", reduceMotion = true) {
                WinCelebration(CelebrationContent.Preview, onDone = {}, windowInsets = PhoneInsets)
            }
        }
        composeRule.onNodeWithText("Link coming soon").performScrollTo().assertExists()
        composeRule.onNodeWithText("Buy me a coffee").performScrollTo().performClick()
        composeRule.onNodeWithText("Not set up yet: there’s no link to open.").assertExists()
    }

    @Test
    fun aPlayingPreviewCanPlayAgainButTheWinCannot() {
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:22") {
                WinCelebration(CelebrationContent.Preview, onDone = {}, windowInsets = PhoneInsets)
            }
        }
        composeRule.onNodeWithText("Play again").performClick()
        composeRule.onNodeWithText("Done").assertExists()
    }

    @Test
    fun aVerifiedWinCelebratesOnItsFirstOpenOnlyAndThenShowsTheResult() {
        val state = fixture.canonWon.resultState(stampSeal = true)
        val heldSeen = mutableListOf<Boolean>()
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:22", reduceMotion = true) {
                CelebratedResult(state, PhoneInsets) { held ->
                    heldSeen += held
                    Text("the result page")
                }
            }
        }
        composeRule.onNodeWithText("See the receipt").assertExists()
        composeRule.onNodeWithText("Won for £101.00", substring = true).assertExists()
        assertTrue("the seal waits under the celebration", heldSeen.last())
        composeRule.onNodeWithText("See the receipt").performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("See the receipt").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("the result page").assertExists()
        assertFalse("the seal stamps as the page comes into view", heldSeen.last())
    }

    @Test
    fun aSecondOpenOfTheWinDoesNotCelebrate() {
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:22", reduceMotion = true) {
                CelebratedResult(
                    fixture.canonWon.resultState(stampSeal = false),
                    PhoneInsets,
                ) { Text("the result page") }
            }
        }
        assertTrue(composeRule.onAllNodesWithText("See the receipt").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("the result page").assertExists()
    }

    @Test
    fun aLostResultDoesNotCelebrate() {
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:22", reduceMotion = true) {
                CelebratedResult(fixture.nintendo64Lost.resultState(stampSeal = true), PhoneInsets) {
                    Text("the result page")
                }
            }
        }
        assertTrue(composeRule.onAllNodesWithText("See the receipt").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun noLabelIsCutAtAnyTextSizeOrWidth() {
        var width by mutableStateOf(411.dp)
        var fontScale by mutableFloatStateOf(1f)
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:22", reduceMotion = true, fontScale = fontScale) {
                Box(Modifier.width(width)) {
                    WinCelebration(
                        CelebrationContent.of(fixture.canonWon.resultState()),
                        onDone = {},
                        windowInsets = PhoneInsets,
                    )
                }
            }
        }
        for (rowWidth in listOf(411.dp, 347.dp, 300.dp)) {
            for (scale in listOf(1f, 1.3f, 1.6f, 2f)) {
                width = rowWidth
                fontScale = scale
                composeRule.waitForIdle()
                // The serif headline wraps rather than cuts; the labels in buttons and rows must stay whole.
                for (label in listOf("See the receipt", "Buy me a coffee", "Link coming soon")) {
                    assertWhole(label, "width $rowWidth, text x$scale")
                }
            }
        }
    }

    /**
     * Shown whole: on screen from end to end, no line cut short and no ellipsis. (A plain Text's own width-overflow
     * flag rounds its fractional width and reports single words as overflowing, so the bounds are checked instead.)
     */
    private fun assertWhole(
        text: String,
        case: String,
    ) {
        val node = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().single()
        val results = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(results)
        val layout = results.single()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = node.boundsInRoot
        assertFalse("\"$text\" is cut short ($case)", layout.didOverflowHeight)
        assertFalse("\"$text\" ends in an ellipsis ($case)", layout.isLineEllipsized(layout.lineCount - 1))
        assertTrue(
            "\"$text\" runs off the screen ($case): $bounds",
            bounds.left >= root.left - 0.5f && bounds.right <= root.right + 0.5f,
        )
    }
}
