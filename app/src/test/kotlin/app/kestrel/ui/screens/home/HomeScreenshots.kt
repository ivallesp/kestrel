package app.kestrel.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.kestrel.model.SnipeStatus
import app.kestrel.ui.components.menuBorder
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotSurface
import app.kestrel.ui.harness.ScreenshotTest
import app.kestrel.ui.screens.add.AddFixtures
import app.kestrel.ui.screens.add.EditMaxSheetBody
import app.kestrel.ui.screens.add.SheetPreview
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

/** S1 in every state DESIGN asks for (Q30 and S1 States), light and dark, with the review pages for rows and pills. */
class HomeScreenshots : ScreenshotTest() {
    private val fixture = CatalogueFixture

    @Composable
    private fun Home(
        state: HomeUiState,
        firstItem: Int = 0,
        snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    ) {
        HomeScreen(
            state = state,
            windowInsets = PhoneInsets,
            listState = rememberLazyListState(initialFirstVisibleItemIndex = firstItem),
            snackbarHostState = snackbarHostState,
        )
    }

    // S1 at 18:08:33.

    @Test
    fun homeLight() = capture("S1-home", darkTheme = false, time = "18:08") { Home(HomeFixtures.home) }

    @Test
    fun homeDark() = capture("S1-home", darkTheme = true, time = "18:08") { Home(HomeFixtures.home) }

    @Test
    fun homeLargeTextLight() =
        capture("S1-home", darkTheme = false, time = "18:08", fontScale = 1.3f) { Home(HomeFixtures.home) }

    @Test
    fun homeMiddleLargeTextLight() =
        capture("S1-home-middle", darkTheme = false, time = "18:08", fontScale = 1.3f) {
            Home(HomeFixtures.home, firstItem = 4)
        }

    @Test
    fun homeScrolledLargeTextLight() =
        capture("S1-home-scrolled", darkTheme = false, time = "18:08", fontScale = 1.3f) {
            Home(HomeFixtures.home, firstItem = END)
        }

    @Test
    fun homeScrolledLight() =
        capture("S1-home-scrolled", darkTheme = false, time = "18:08") { Home(HomeFixtures.home, firstItem = END) }

    @Test
    fun homeScrolledDark() =
        capture("S1-home-scrolled", darkTheme = true, time = "18:08") { Home(HomeFixtures.home, firstItem = END) }

    @Test
    fun emptyLight() = capture("S1-empty", darkTheme = false, time = "18:08") { Home(HomeFixtures.empty) }

    @Test
    fun emptyDark() = capture("S1-empty", darkTheme = true, time = "18:08") { Home(HomeFixtures.empty) }

    @Test
    fun emptyLargeTextLight() =
        capture("S1-empty", darkTheme = false, time = "18:08", fontScale = 1.3f) { Home(HomeFixtures.empty) }

    @Test
    fun offlineLight() =
        capture("S1-offline", darkTheme = false, time = "18:08", online = false) { Home(HomeFixtures.offline) }

    @Test
    fun offlineDark() =
        capture("S1-offline", darkTheme = true, time = "18:08", online = false) { Home(HomeFixtures.offline) }

    @Test
    fun signedOutLight() =
        capture("S1-signed-out", darkTheme = false, time = "18:08") { Home(HomeFixtures.signedOutHome) }

    @Test
    fun signedOutDark() =
        capture("S1-signed-out", darkTheme = true, time = "18:08") { Home(HomeFixtures.signedOutHome) }

    @Test
    fun refreshingLight() =
        capture("S1-refreshing", darkTheme = false, time = "18:08") {
            Home(HomeFixtures.home.copy(refreshing = true))
        }

    // The hero's variants (DESIGN 3.4).

    @Test
    fun attentionHeroLight() =
        capture("S1-hero-attention", darkTheme = false, time = "18:22") { Home(HomeFixtures.attentionHero) }

    @Test
    fun attentionHeroDark() =
        capture("S1-hero-attention", darkTheme = true, time = "18:22") { Home(HomeFixtures.attentionHero) }

    @Test
    fun finalMinuteHeroLight() =
        capture("S1-hero-final-minute", darkTheme = false, time = "18:21") { Home(HomeFixtures.finalMinuteHero) }

    @Test
    fun finalMinuteHeroDark() =
        capture("S1-hero-final-minute", darkTheme = true, time = "18:21") { Home(HomeFixtures.finalMinuteHero) }

    @Test
    fun bidSentHeroLight() =
        capture("S1-hero-bid-sent", darkTheme = false, time = "18:21") { Home(HomeFixtures.bidSentHero) }

    @Test
    fun bidSentHeroReducedMotionLight() =
        capture("S1-hero-bid-sent-reduced-motion", darkTheme = false, time = "18:21", reduceMotion = true) {
            Home(HomeFixtures.bidSentHero)
        }

    @Test
    fun inTheLeadHeroLight() =
        capture("S1-hero-in-the-lead", darkTheme = false, time = "18:21") { Home(HomeFixtures.inTheLeadHero) }

    @Test
    fun outbidHeroLight() =
        capture("S1-hero-outbid", darkTheme = false, time = "18:21") { Home(HomeFixtures.outbidHero) }

    @Test
    fun extendedHeroLight() =
        capture("S1-hero-extended", darkTheme = false, time = "19:55") { Home(HomeFixtures.extendedHero) }

    // Snackbars (DESIGN 3.20).

    @Test
    fun raiseUndoLight() =
        capture("S1-undo-raise", darkTheme = false, time = "18:08") {
            Snackbar(HomeFixtures.raised, "Max raised to £2,200.00")
        }

    @Test
    fun stopUndoDark() =
        capture("S1-undo-stop", darkTheme = true, time = "18:08") {
            Snackbar(HomeFixtures.stopped, "Stopped sniping Omega Speedmaster")
        }

    @Composable
    private fun Snackbar(
        state: HomeUiState,
        text: String,
    ) {
        val host = remember { SnackbarHostState() }
        LaunchedEffect(host) { host.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Indefinite) }
        Home(state, snackbarHostState = host)
    }

    // The Raise max menu, opened from the Omega's split button. Robolectric lays the menu's popup window out (272 x
    // 113 dp at 116, 680 dp) but Roborazzi draws no popup window, so the page draws the same items in Material's menu
    // container at that spot, over the screen with the split button open.

    @Test
    fun raiseMenuLight() = raiseMenu(darkTheme = false)

    @Test
    fun raiseMenuDark() = raiseMenu(darkTheme = true)

    private fun raiseMenu(darkTheme: Boolean) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ScreenshotSurface(darkTheme = darkTheme, time = "18:08") {
                Box {
                    Home(HomeFixtures.home)
                    MenuAsDrawn(Modifier.offset(x = 116.dp, y = 680.dp))
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(DEFAULT_ADVANCE_MILLIS)
        composeRule.onNodeWithContentDescription("More for this snipe").performClick()
        composeRule.mainClock.advanceTimeBy(DEFAULT_ADVANCE_MILLIS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/${fileName("S1-raise-menu", darkTheme, 1f)}.png")
    }

    /** The menu's items in the container DropdownMenu draws them in: its shape, colour, elevation and padding. */
    @Composable
    private fun MenuAsDrawn(modifier: Modifier = Modifier) {
        Surface(
            modifier = modifier.width(IntrinsicSize.Max),
            shape = MenuDefaults.shape,
            color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
            border = menuBorder(),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                RaiseMaxMenuItems(minimum = fixture.gbp("2200.00"), onQuickRaise = {}, onStopSniping = {})
            }
        }
    }

    // Review pages.

    @Test
    fun dockStatesLight() = capture("S1-dock-states", darkTheme = false, time = "18:08") { DockStatesPage() }

    @Test
    fun dockStatesDark() = capture("S1-dock-states", darkTheme = true, time = "18:08") { DockStatesPage() }

    @Test
    fun dockStatesLargeTextFirstHalfLight() =
        capture("S1-dock-states-1", darkTheme = false, time = "18:08", fontScale = 1.3f) {
            DockStatesPage(states = dockStates.take(4))
        }

    @Test
    fun dockStatesLargeTextSecondHalfLight() =
        capture("S1-dock-states-2", darkTheme = false, time = "18:08", fontScale = 1.3f) {
            DockStatesPage(states = dockStates.drop(4))
        }

    @Test
    fun rowsWatchingAndAttentionLight() =
        capture("S1-rows-1", darkTheme = false, time = "18:08") {
            RowStatesPage(
                "Rows: open",
                listOf(
                    fixture.polaroidWatching,
                    fixture.miniDiscArmed,
                    fixture.omegaOverMax,
                    fixture.canonSignInNeeded,
                ),
            )
        }

    @Test
    fun rowsHandOffsLight() =
        capture("S1-rows-2", darkTheme = false, time = "18:12") {
            RowStatesPage(
                "Rows: needs you",
                listOf(fixture.canonCaptcha, fixture.canonPhoneCheck, fixture.canonDryRunFailed, fixture.omegaNeedsYou),
            )
        }

    @Test
    fun rowsAtRiskLight() =
        capture("S1-rows-3", darkTheme = false, time = "18:08") {
            RowStatesPage("Rows: at risk", listOf(fixture.canonOffline, fixture.canonUnreachable))
        }

    @Test
    fun rowsBiddingLight() =
        capture("S1-rows-4", darkTheme = false, time = "18:21") {
            RowStatesPage(
                "Rows: bidding",
                listOf(fixture.canonFinalMinute, fixture.canonBidSent, fixture.canonInTheLead, fixture.canonOutbid),
            )
        }

    @Test
    fun rowsBiddingDark() =
        capture("S1-rows-4", darkTheme = true, time = "18:21") {
            RowStatesPage(
                "Rows: bidding",
                listOf(fixture.canonFinalMinute, fixture.canonBidSent, fixture.canonInTheLead, fixture.canonOutbid),
            )
        }

    @Test
    fun rowsAfterTheBidLight() =
        capture("S1-rows-5", darkTheme = false, time = "18:21") {
            RowStatesPage(
                "Rows: after the bid",
                listOf(fixture.canonNoReply, fixture.walkmanExtended, fixture.canonReadingResult),
            )
        }

    @Test
    fun rowsFinishedLight() =
        capture("S1-rows-6", darkTheme = false, time = "18:08") {
            RowStatesPage(
                "Rows: finished",
                listOf(
                    fixture.gameAndWatchWon,
                    fixture.nintendo64Lost,
                    fixture.gameBoyEndedEarly,
                    fixture.gameBoyCancelled,
                ),
            )
        }

    @Test
    fun rowsFinishedDark() =
        capture("S1-rows-6", darkTheme = true, time = "18:08") {
            RowStatesPage(
                "Rows: finished",
                listOf(
                    fixture.gameAndWatchWon,
                    fixture.nintendo64Lost,
                    fixture.gameBoyEndedEarly,
                    fixture.gameBoyCancelled,
                ),
            )
        }

    @Test
    fun rowsFailedLight() =
        capture("S1-rows-7", darkTheme = false, time = "18:22") {
            RowStatesPage(
                "Rows: cancelled and failed",
                listOf(fixture.walkmanCancelledByGroup, fixture.canonBidNotSent, fixture.canonBidFailed),
                companions = listOf(fixture.miniDiscArmed.copy(snipe = wonMiniDisc())),
            )
        }

    @Test
    fun rowsLargeTextLight() =
        capture("S1-rows-1", darkTheme = false, time = "18:08", fontScale = 1.3f) {
            RowStatesPage("Rows: open", listOf(fixture.omegaOverMax, fixture.walkmanArmed, fixture.polaroidWatching))
        }

    // "Raise max" on the Omega row opens the raise sheet over the list (DESIGN S1 behaviour).

    @Test
    fun raiseSheetOverTheListLight() = raiseSheetOverTheList(darkTheme = false)

    @Test
    fun raiseSheetOverTheListDark() = raiseSheetOverTheList(darkTheme = true)

    private fun raiseSheetOverTheList(darkTheme: Boolean) =
        capture("S1-raise-max-sheet", darkTheme = darkTheme, time = "18:08") {
            SheetPreview(title = AddFixtures.raiseSheet.title, backdrop = { Home(HomeFixtures.home) }) {
                EditMaxSheetBody(state = AddFixtures.raiseSheet)
            }
        }

    private fun wonMiniDisc() = fixture.miniDisc.copy(status = SnipeStatus.WON)

    private companion object {
        /** Past the last item: the list clamps to its end. */
        const val END = 100
    }
}
