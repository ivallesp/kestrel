package app.kestrel.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotSurface
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S1 for TalkBack and for fingers: touch targets (Q13), one sentence per row (3.5, Q18) and where taps lead. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fixture = CatalogueFixture
    private val listState = LazyListState()

    private fun show(
        state: HomeUiState,
        actions: HomeActions = HomeActions(),
    ) {
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:08", reduceMotion = true) {
                HomeScreen(state, windowInsets = PhoneInsets, listState = listState, actions = actions)
            }
        }
    }

    private fun scrollToEnd() {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("See all finished snipes"))
        composeRule.waitForIdle()
    }

    private fun assertTouchTargets(nodes: List<SemanticsNode>) {
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue(nodes.isNotEmpty())
        nodes.forEach { node ->
            // A row half-hidden by the dock has clipped bounds; its touch target is the whole row.
            val touch = node.touchBoundsInRoot
            val width = maxOf(touch.width, node.size.width.toFloat())
            val height = maxOf(touch.height, node.size.height.toFloat())
            val name = node.config.toString().take(120)
            assertTrue("$name is $width x $height px", width >= minimum && height >= minimum)
        }
    }

    @Test
    fun everyClickableOnTheListIsAtLeast48DpSquare() {
        show(HomeFixtures.home)
        assertTouchTargets(composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes())
        scrollToEnd()
        assertTouchTargets(composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes())
    }

    @Test
    fun everyClickableOfTheOtherStatesIsAtLeast48DpSquare() {
        var state by mutableStateOf(HomeFixtures.signedOutHome)
        composeRule.setContent {
            ScreenshotSurface(darkTheme = false, time = "18:08", reduceMotion = true) {
                HomeScreen(state, windowInsets = PhoneInsets)
            }
        }
        listOf(HomeFixtures.signedOutHome, HomeFixtures.severalProblems, HomeFixtures.attentionHero, HomeFixtures.empty)
            .forEach { next ->
                state = next
                composeRule.waitForIdle()
                assertTouchTargets(composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes())
            }
    }

    @Test
    fun aRowIsOneSentenceWithItsActions() {
        show(HomeFixtures.home)
        val omega =
            HomeFixtures.home.sections
                .first()
                .blocks
                .first()
                .lots[1]
        val node = composeRule.onNodeWithContentDescription(HomeText.spokenRow(omega, fixture.now)).fetchSemanticsNode()
        assertEquals("Open snipe", node.config[SemanticsActions.OnClick].label)
        assertEquals(listOf("Raise max", "Stop sniping"), node.config[SemanticsActions.CustomActions].map { it.label })
    }

    @Test
    fun theTitleAndTheSectionHeadsAreHeadings() {
        show(HomeFixtures.home)
        composeRule.onNode(hasText("Snipes") and isHeading()).assertExists()
        composeRule.onNode(hasText("ENDING SOON", substring = true) and isHeading()).assertExists()
        composeRule.onNode(hasText("THIS WEEK", substring = true) and isHeading()).assertExists()
    }

    @Test
    fun theMenuRaisesToTheNextBidOrStopsSniping() {
        var raised: Long? = null
        var stopped: Long? = null
        var sheet: Long? = null
        show(
            HomeFixtures.home,
            HomeActions(onQuickRaise = { raised = it }, onStopSniping = { stopped = it }, onRaiseMax = { sheet = it }),
        )
        composeRule.onNodeWithText("Raise max").performClick()
        assertEquals(fixture.omega.id, sheet)
        composeRule.onNodeWithContentDescription("More for this snipe").performClick()
        composeRule.onNodeWithText("Raise to £2,200.00, the next bid").performClick()
        assertEquals(fixture.omega.id, raised)
        composeRule.onNodeWithContentDescription("More for this snipe").performClick()
        composeRule.onNodeWithText("Stop sniping").performClick()
        assertEquals(fixture.omega.id, stopped)
    }

    @Test
    fun thePillOpensTheChecksAndItsFixOpensTheBatteryPage() {
        var action: PillAction? = null
        var openedReady = false
        show(HomeFixtures.home, HomeActions(onReadinessAction = { action = it }, onOpenReady = { openedReady = true }))
        composeRule.onNodeWithText("Fix").performClick()
        assertEquals(PillAction.Fix(ReadinessKey.BATTERY), action)
        composeRule.onNodeWithContentDescription("Battery restricted. A bid could go out late").performClick()
        assertTrue(openedReady)
    }

    @Test
    fun tapsLeadToTheirScreens() {
        val opened = mutableListOf<String>()
        show(
            HomeFixtures.home,
            HomeActions(
                onOpenLot = { opened += "lot $it" },
                onOpenResult = { opened += "result $it" },
                onOpenGroup = { opened += "group $it" },
                onOpenSettings = { opened += "settings" },
                onAdd = { opened += "add" },
                onLotAction = { id, action -> opened += "${action.label} $id" },
                onSeeAllFinished = { opened += "history" },
            ),
        )
        composeRule.onNodeWithContentDescription("Status: Armed").performClick()
        composeRule.onNodeWithContentDescription("Settings and eBay account, lot.42").performClick()
        composeRule.onNodeWithContentDescription("Add snipe").performClick()
        composeRule.onNodeWithText("Portable audio: win 1 of 2", substring = true).performClick()
        scrollToEnd()
        composeRule.onNodeWithText(LotAction.SET_MAX.label).performClick()
        composeRule
            .onNodeWithContentDescription(HomeText.spokenTitle(fixture.gameAndWatch.title), substring = true)
            .performClick()
        composeRule.onNodeWithText("See all finished snipes").performClick()
        assertEquals(
            listOf("lot 1", "settings", "add", "group 1", "Set a max 5", "result 7", "history"),
            opened,
        )
    }

    @Test
    fun theEmptyListOffersPasteInsteadOfAdd() {
        var added = false
        show(HomeFixtures.empty, HomeActions(onAdd = { added = true }))
        composeRule.onNodeWithContentDescription("Add snipe").assertDoesNotExist()
        composeRule.onNodeWithText("Paste eBay link").performClick()
        assertTrue(added)
        composeRule.onAllNodesWithText("Nothing on the list yet").fetchSemanticsNodes().single()
    }

    @Test
    fun aPriceAndItsMetaShareABaselineAndWrapUnderIt() {
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                Column {
                    BaselineFlow(horizontalGap = 8.dp) {
                        Text("£24.00", style = KestrelTheme.type.priceRow)
                        Text("5 bids · £3.49 postage", style = MaterialTheme.typography.bodyMedium)
                    }
                    BaselineFlow(horizontalGap = 8.dp, modifier = Modifier.width(140.dp)) {
                        Text("£2,150.00", style = KestrelTheme.type.priceRow)
                        Text("31 bids · £12.95 postage", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        assertEquals(baselineOf("£24.00"), baselineOf("5 bids · £3.49 postage"), 1f)
        assertTrue(baselineOf("31 bids · £12.95 postage") > baselineOf("£2,150.00") + 10f)
    }

    private fun baselineOf(text: String): Float {
        val node = composeRule.onNodeWithText(text).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return node.positionInRoot.y + layouts.single().firstBaseline
    }
}
