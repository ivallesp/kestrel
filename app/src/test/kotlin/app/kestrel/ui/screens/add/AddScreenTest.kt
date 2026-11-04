package app.kestrel.ui.screens.add

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** S2's touch targets (DESIGN Q13), its buttons' states (Q27) and what each tap asks for. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class AddScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Records every action S2 asks for, by name. */
    private class RecordingActions : AddActions {
        val asked = mutableListOf<String>()

        override fun close() {
            asked += "close"
        }

        override fun viewListing(url: String) {
            asked += "view $url"
        }

        override fun paste() {
            asked += "paste"
        }

        override fun retry() {
            asked += "retry"
        }

        override fun pasteDifferentLink() {
            asked += "different"
        }

        override fun openSnipe(snipeId: Long) {
            asked += "open $snipeId"
        }

        override fun useOddPence() {
            asked += "odd pence"
        }

        override fun selectLead(index: Int) {
            asked += "lead $index"
        }

        override fun chooseGroup() {
            asked += "group"
        }

        override fun arm() {
            asked += "arm"
        }

        override fun confirmWords() {
            asked += "confirm"
        }
    }

    private fun show(
        state: AddUiState,
        actions: AddActions = RecordingActions(),
        canPaste: Boolean = false,
    ) {
        composeRule.setContent {
            Themed {
                AddScreen(
                    state,
                    actions = actions,
                    windowInsets = PhoneInsets,
                    canPaste = canPaste,
                    focusMax = false,
                )
            }
        }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KestrelTheme(darkTheme = false, reduceMotion = true, content = content)
    }

    /** Every clickable but the text fields (whose whole row is the field) is at least 48 dp square. */
    private fun assertTouchTargets() {
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        val small =
            composeRule
                .onAllNodes(hasClickAction(), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { it.config.getOrNull(SemanticsActions.SetText) == null }
                .filter { it.touchBoundsInRoot.width < minimum || it.touchBoundsInRoot.height < minimum }
                .map { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: node.toString() }
        assertTrue("Touch targets under 48 dp: $small", small.isEmpty())
    }

    @Test
    fun theKeyboardsDoneKeyOnTheMaxFieldDoesNotArm() {
        val actions = RecordingActions()
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                AddScreen(
                    AddFixtures.ready(maxText = "90.00"),
                    actions = actions,
                    windowInsets = PhoneInsets,
                    focusMax = false,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Your max bid").performClick().performImeAction()
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), actions.asked)
    }

    @Test
    fun theSetMaxSheetsDoneKeyDoesNotArm() {
        var saves = 0
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                EditMaxSheetBody(
                    state = AddFixtures.setSheet,
                    actions =
                        object : EditMaxActions {
                            override fun save() {
                                saves++
                            }
                        },
                    focusMax = false,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Your max bid").performClick().performImeAction()
        composeRule.waitForIdle()
        assertEquals(0, saves)
    }

    @Test
    fun everyClickableOnThePlanIsAtLeast48Dp() {
        show(AddFixtures.ready())
        assertTouchTargets()
    }

    @Test
    fun everyClickableOnTheNoticesIsAtLeast48Dp() {
        show(AddFixtures.fetchFailed)
        assertTouchTargets()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.onNodeWithText("Paste a different link").performClick()
    }

    @Test
    fun armIsEnabledFromTheMinimumAndArms() {
        val actions = RecordingActions()
        show(AddFixtures.ready(), actions)
        composeRule.onNodeWithText("Arm snipe").assertIsEnabled().performClick()
        assertEquals(listOf("arm"), actions.asked)
    }

    @Test
    fun armIsDisabledBelowTheMinimumAndTheErrorIsSpoken() {
        show(AddFixtures.tooLow)
        composeRule.onNodeWithText("Arm snipe").assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription("Your max bid")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Too low: eBay needs at least £66.08"))
    }

    @Test
    fun anEmptyMaxOffersToWatch() {
        val actions = RecordingActions()
        show(AddFixtures.emptyMax, actions)
        composeRule.onNodeWithText("No max yet: Kestrel will only watch the price.").assertIsDisplayed()
        composeRule.onNodeWithText("Watch without a max").performClick()
        assertEquals(listOf("arm"), actions.asked)
    }

    @Test
    fun pasteShowsOnlyWhenTheClipboardHoldsALink() {
        val actions = RecordingActions()
        show(AddFixtures.linkEntry, actions, canPaste = true)
        composeRule.onNode(hasText("Paste") and hasClickAction()).performClick()
        assertEquals(listOf("paste"), actions.asked)
        composeRule.onNodeWithText("Get listing").assertIsNotEnabled()
        assertTouchTargets()
    }

    @Test
    fun withoutALinkOnTheClipboardThereIsNoPaste() {
        show(AddFixtures.linkEntry, canPaste = false)
        assertEquals(0, composeRule.onAllNodes(hasText("Paste") and hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun theAlreadySnipedPageOpensTheSnipe() {
        val actions = RecordingActions()
        show(AddFixtures.alreadySniped, actions)
        composeRule.onNodeWithText("Open snipe").performClick()
        composeRule.onNodeWithText("View listing").performClick()
        assertEquals(listOf("open 1", "view ${AddFixtures.alreadySniped.page.listingUrl}"), actions.asked)
    }

    @Test
    fun theOddPenceCardAndTheGroupRowAct() {
        val actions = RecordingActions()
        show(AddFixtures.ready(), actions)
        composeRule.onNodeWithContentDescription("Use £90.37").performScrollTo().performClick()
        composeRule.onNodeWithText("Add to a group").performScrollTo().performClick()
        composeRule.onNodeWithText(secondsText(MaxRules.LeadOptions.last().toLong())).performScrollTo().performClick()
        assertEquals(listOf("odd pence", "group", "lead 2"), actions.asked)
    }

    @Test
    fun theWordsCheckGoesAheadOnlyWhenConfirmed() {
        val actions = RecordingActions()
        show(AddFixtures.wordsCheck, actions)
        composeRule.onNodeWithText("Nine hundred pounds").assertIsDisplayed()
        composeRule.onNode(hasText("Change max") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        composeRule.onNode(hasText("Arm snipe") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("confirm"), actions.asked)
    }
}
