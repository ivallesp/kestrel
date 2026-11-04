package app.kestrel.ui.screens.lot

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotSurface
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.screens.lot.LotFixtures.lotState
import app.kestrel.ui.screens.lot.LotFixtures.resultState
import app.kestrel.ui.screens.result.ResultScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** S3, S4 and the edit sheet as TalkBack and a finger meet them: touch targets (Q13), spoken sentences and actions. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class LotScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fixture = CatalogueFixture

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent {
            ScreenshotSurface(
                darkTheme = false,
                time = "18:21",
                reduceMotion = true,
                content = content,
            )
        }
    }

    /** Every clickable on screen is at least 48 dp square (DESIGN Q13). */
    private fun assertTouchTargets() {
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        val clickables = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("no clickables found", clickables.isNotEmpty())
        clickables.forEach { node ->
            val touch = node.touchBoundsInRoot
            val name =
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                    ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            assertTrue(
                "$name is ${touch.width} x ${touch.height} px",
                touch.width >= minimum - 1 && touch.height >= minimum - 1,
            )
        }
    }

    @Test
    fun theFinalMinuteIsSpokenAsSentencesAndEveryTargetIsLargeEnough() {
        val actions = mutableListOf<LotAction>()
        show {
            LotScreen(
                fixture.canonFinalMinute.lotState(),
                windowInsets = PhoneInsets,
                onAction = { actions += it },
            )
        }
        assertTouchTargets()
        composeRule.onNodeWithContentDescription("36 seconds left. Kestrel bids in 30 seconds.").assertExists()
        composeRule
            .onNodeWithContentDescription("Final minute: 36 seconds left. Kestrel bids in 30 seconds.")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        composeRule.onNodeWithContentDescription("Ends at 18:21:40 GMT today. Kestrel bids at 18:21:34.").assertExists()
        composeRule.onNodeWithContentDescription("Status: Final minute").assertExists()
        composeRule.onNodeWithText(fixture.canon.title.forDisplay()).assert(isHeading())
        composeRule.onNodeWithText("Edit max").performScrollTo().performClick()
        assertEquals(listOf(LotAction.EDIT_MAX), actions)
    }

    @Test
    fun theOverflowOffersOpenOnEbayAndStopSniping() {
        var opened = 0
        var stopped = 0
        show {
            LotScreen(
                LotFixtures.canonArmedToday.lotState(),
                windowInsets = PhoneInsets,
                onOpenOnEbay = { opened++ },
                onStopSniping = { stopped++ },
            )
        }
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Open on eBay").assertExists()
        composeRule.onNodeWithText("Delete").assertExists()
        composeRule.onNodeWithText("Stop sniping").performClick()
        assertEquals(1, stopped)
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Open on eBay").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun anAttentionCardsActionIsReachable() {
        val actions = mutableListOf<LotAction>()
        show { LotScreen(fixture.canonCaptcha.lotState(), windowInsets = PhoneInsets, onAction = { actions += it }) }
        assertTouchTargets()
        composeRule.onNodeWithText("Open eBay page").performClick()
        assertEquals(listOf(LotAction.OPEN_EBAY_PAGE), actions)
    }

    @Test
    fun theResultsButtonsAreLargeEnoughAndShare() {
        var shared = 0
        show { ResultScreen(fixture.canonWon.resultState(), windowInsets = PhoneInsets, onShare = { shared++ }) }
        assertTouchTargets()
        composeRule.onNodeWithText("You won").assert(isHeading())
        composeRule.onNodeWithContentDescription("Share receipt").performClick()
        assertEquals(1, shared)
        composeRule
            .onNodeWithContentDescription("eBay replied in 0.8 seconds: Your current bid puts you in the lead.")
            .assertExists()
    }

    @Test
    fun aBidThatWasNotSentOffersSignIn() {
        val actions = mutableListOf<LotAction>()
        show {
            ResultScreen(fixture.canonBidNotSent.resultState(), windowInsets = PhoneInsets, onAction = {
                actions +=
                    it
            })
        }
        assertTouchTargets()
        composeRule.onNodeWithText("Sign in").performClick()
        assertEquals(listOf(LotAction.SIGN_IN), actions)
    }

    @Test
    fun theSetMaxSheetsControlsAreLargeEnough() {
        val moment = fixture.polaroidWatching
        val state = moment.lotState(SheetRequest(SheetKind.Set, leadSeconds = 6))
        var leads = listOf<Int>()
        show {
            EditMaxSheetContent(
                state = state.sheet!!,
                input = TextFieldState("70"),
                now = state.now,
                onSave = {},
                onClose = {},
                onUseAmount = {},
                onSelectLead = { leads = leads + it },
            )
        }
        assertTouchTargets()
        composeRule.onNodeWithText("Make it £70.37").assertExists()
        composeRule.onNodeWithText("10\u202Fs").performClick()
        assertEquals(listOf(10), leads)
    }
}
