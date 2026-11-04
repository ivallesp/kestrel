package app.kestrel.ui.screens.group

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Group screen's touch targets (DESIGN Q13), row semantics (Q18) and taps. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class GroupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingActions : GroupActions {
        val asked = mutableListOf<String>()

        override fun back() {
            asked += "back"
        }

        override fun open(row: GroupLotRowState) {
            asked += "open ${row.snipeId}"
        }

        override fun act(
            snipeId: Long,
            action: LotAction,
        ) {
            asked += "${action.label} $snipeId"
        }

        override fun addSnipe() {
            asked += "add"
        }
    }

    private fun show(
        state: GroupUiState,
        actions: GroupActions,
    ) {
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                GroupScreen(state = state, actions = actions, windowInsets = PhoneInsets)
            }
        }
    }

    @Test
    fun everyClickableIsAtLeast48Dp() {
        show(GroupFixtures.members, RecordingActions())
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        val small =
            composeRule
                .onAllNodes(hasClickAction(), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { it.touchBoundsInRoot.width < minimum || it.touchBoundsInRoot.height < minimum }
                .map { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: it.toString() }
        assertTrue("Touch targets under 48 dp: $small", small.isEmpty())
    }

    @Test
    fun rowsOpenTheirLotAndTheButtonsAct() {
        val actions = RecordingActions()
        show(GroupFixtures.members, actions)
        // The title on screen keeps "WM-FX421" whole with a word joiner, so the row is found by its sentence.
        composeRule.onNodeWithContentDescription("Sony Walkman WM-FX421.", substring = true).performClick()
        composeRule.onNodeWithText("Add a snipe to this group").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("open 4", "add", "back"), actions.asked)
    }

    @Test
    fun aRowIsOneSentenceWithItsActionForTalkBack() {
        val watched = CatalogueFixture.polaroid.copy(groupId = CatalogueFixture.portableAudio.id)
        val state =
            GroupFixtures.state(
                snipes =
                    CatalogueFixture.snipes.map {
                        if (it.id ==
                            watched.id
                        ) {
                            watched
                        } else {
                            it
                        }
                    },
            )
        val actions = RecordingActions()
        show(state, actions)
        val row =
            composeRule.onNodeWithContentDescription(
                "Polaroid SX-70 Land Camera. Watching, no max yet.",
                substring = true,
            )
        val custom = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Set a max"), custom.map { it.label })
        custom.single().action()
        assertEquals(listOf("Set a max 5"), actions.asked)
        row.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("open 5", actions.asked.last())
    }
}
