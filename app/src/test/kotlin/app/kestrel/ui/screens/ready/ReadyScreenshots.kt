package app.kestrel.ui.screens.ready

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import org.junit.Test

/** S5 in every state it has: the DESIGN fixture, all good, signed out, nothing armed and the first check. */
class ReadyScreenshots : ScreenshotTest() {
    @Composable
    private fun Ready(
        state: ReadyUiState,
        listState: LazyListState = rememberLazyListState(),
    ) {
        ReadyScreen(
            state = state,
            onBack = {},
            onCheckAgain = {},
            onAction = {},
            windowInsets = PhoneInsets,
            listState = listState,
        )
    }

    @Test
    fun readyLight() = capture("S5-ready", darkTheme = false, time = "18:13") { Ready(ReadyFixtures.state()) }

    @Test
    fun readyDark() = capture("S5-ready", darkTheme = true, time = "18:13") { Ready(ReadyFixtures.state()) }

    @Test
    fun readyLargeText() =
        capture("S5-ready", darkTheme = false, time = "18:13", fontScale = 1.3f) { Ready(ReadyFixtures.state()) }

    /** The rest of the page: the all-good rows and the cadence footer above the gesture handle. */
    @Test
    fun readyScrolledLight() =
        capture("S5-ready-scrolled", darkTheme = false, time = "18:13") {
            Ready(ReadyFixtures.state(), rememberLazyListState(initialFirstVisibleItemIndex = SCROLLED_TO))
        }

    @Test
    fun readyScrolledDark() =
        capture("S5-ready-scrolled", darkTheme = true, time = "18:13") {
            Ready(ReadyFixtures.state(), rememberLazyListState(initialFirstVisibleItemIndex = SCROLLED_TO))
        }

    @Test
    fun allGoodLight() =
        capture("S5-ready-all-good", darkTheme = false, time = "18:13") {
            Ready(ReadyFixtures.state(FakePhone(batteryUnrestricted = true, promotedNotifications = true)))
        }

    @Test
    fun allGoodDark() =
        capture("S5-ready-all-good", darkTheme = true, time = "18:13") {
            Ready(ReadyFixtures.state(FakePhone(batteryUnrestricted = true, promotedNotifications = true)))
        }

    @Test
    fun signedOutLight() =
        capture("S5-ready-signed-out", darkTheme = false, time = "18:13") {
            Ready(ReadyFixtures.state(FakePhone(signedIn = false)))
        }

    @Test
    fun nothingArmedLight() =
        capture("S5-ready-nothing-armed", darkTheme = false, time = "18:13") {
            Ready(
                ReadyFixtures.state(FakePhone(batteryUnrestricted = true), snipes = ReadyFixtures.notArmedSnipes),
                rememberLazyListState(initialFirstVisibleItemIndex = SCROLLED_TO),
            )
        }

    @Test
    fun checkingLight() =
        capture("S5-ready-checking", darkTheme = false, time = "18:13") { Ready(ReadyFixtures.checking) }

    private companion object {
        /** The ALL GOOD head: the header, two section heads and two rows come before it. */
        const val SCROLLED_TO = 5
    }
}
