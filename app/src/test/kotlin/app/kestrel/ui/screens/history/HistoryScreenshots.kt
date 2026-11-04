package app.kestrel.ui.screens.history

import androidx.compose.runtime.Composable
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import org.junit.Test

/** History (DESIGN History and Q30), light and dark, with older months, the empty state and large text. */
class HistoryScreenshots : ScreenshotTest() {
    @Composable
    private fun History(state: HistoryUiState) {
        HistoryScreen(state = state, onBack = {}, onOpenResult = {}, windowInsets = PhoneInsets)
    }

    @Test
    fun historyLight() = capture("history", darkTheme = false, time = "18:08") { History(HistoryFixtures.history) }

    @Test
    fun historyDark() = capture("history", darkTheme = true, time = "18:08") { History(HistoryFixtures.history) }

    @Test
    fun historyLargeTextLight() =
        capture("history", darkTheme = false, time = "18:08", fontScale = 1.3f) { History(HistoryFixtures.history) }

    @Test
    fun olderMonthsLight() =
        capture("history-months", darkTheme = false, time = "18:08") { History(HistoryFixtures.olderHistory) }

    @Test
    fun olderMonthsDark() =
        capture("history-months", darkTheme = true, time = "18:08") { History(HistoryFixtures.olderHistory) }

    @Test
    fun emptyLight() = capture("history-empty", darkTheme = false, time = "18:08") { History(HistoryFixtures.empty) }

    @Test
    fun emptyDark() = capture("history-empty", darkTheme = true, time = "18:08") { History(HistoryFixtures.empty) }
}
