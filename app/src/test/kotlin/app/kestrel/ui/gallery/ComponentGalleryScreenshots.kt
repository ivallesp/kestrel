package app.kestrel.ui.gallery

import app.kestrel.ui.harness.ScreenshotTest
import org.junit.Test

/** The components gallery and the launcher icon board (S7), in both themes. */
class ComponentGalleryScreenshots : ScreenshotTest() {
    @Test
    fun statesLight() = capture("gallery-states", darkTheme = false, time = "18:08") { StatesPage() }

    @Test
    fun statesDark() = capture("gallery-states", darkTheme = true, time = "18:08") { StatesPage() }

    @Test
    fun chipsLight() = capture("gallery-chips", darkTheme = false, time = "18:08") { ChipsPage() }

    @Test
    fun chipsDark() = capture("gallery-chips", darkTheme = true, time = "18:08") { ChipsPage() }

    @Test
    fun moneyLight() = capture("gallery-money", darkTheme = false, time = "18:08") { MoneyPage() }

    @Test
    fun moneyDark() = capture("gallery-money", darkTheme = true, time = "18:08") { MoneyPage() }

    @Test
    fun typeLight() = capture("gallery-type", darkTheme = false, time = "18:21") { TypePage() }

    @Test
    fun typeDark() = capture("gallery-type", darkTheme = true, time = "18:21") { TypePage() }

    @Test
    fun timelineLight() = capture("gallery-timeline", darkTheme = false, time = "18:21") { TimelinePage() }

    @Test
    fun timelineDark() = capture("gallery-timeline", darkTheme = true, time = "18:21") { TimelinePage() }

    @Test
    fun platesLight() = capture("gallery-plates", darkTheme = false, time = "18:08") { PlatesPage() }

    @Test
    fun platesDark() = capture("gallery-plates", darkTheme = true, time = "18:08") { PlatesPage() }

    @Test
    fun controlsLight() = capture("gallery-controls", darkTheme = false, time = "18:09") { ControlsPage() }

    @Test
    fun controlsDark() = capture("gallery-controls", darkTheme = true, time = "18:09") { ControlsPage() }

    @Test
    fun controlsReducedMotion() =
        capture("gallery-controls-reduced-motion", darkTheme = false, time = "18:09", reduceMotion = true) {
            ControlsPage(reduceMotionSample = true)
        }

    // Large text (DESIGN Q2): the long pages split in two, so every label is shown whole at font scale 1.3.

    @Test
    fun statesLargeText() =
        capture("gallery-states-1", darkTheme = false, time = "18:08", fontScale = 1.3f) {
            StatesPage(title = "State labels, 1 of 2", moments = statusMoments.take(STATES_PER_PART))
        }

    @Test
    fun statesLargeTextContinued() =
        capture("gallery-states-2", darkTheme = false, time = "18:08", fontScale = 1.3f) {
            StatesPage(title = "State labels, 2 of 2", moments = statusMoments.drop(STATES_PER_PART))
        }

    @Test
    fun moneyGridsLargeText() =
        capture("gallery-money-grids", darkTheme = false, time = "18:08", fontScale = 1.3f) { MoneyGridsPage() }

    @Test
    fun moneyTotalsLargeText() =
        capture("gallery-money-totals", darkTheme = false, time = "18:08", fontScale = 1.3f) { MoneyTotalsPage() }

    @Test
    fun timelineLargeText() =
        capture("gallery-timeline", darkTheme = false, time = "18:21", fontScale = 1.3f) { LotTimelinePage() }

    @Test
    fun receiptLargeText() =
        capture("gallery-receipt", darkTheme = false, time = "18:21", fontScale = 1.3f) { ReceiptPage() }

    @Test
    fun iconBoardLight() = capture("S7-icon", darkTheme = false, time = "18:08") { IconBoard() }

    @Test
    fun iconBoardDark() = capture("S7-icon", darkTheme = true, time = "18:08") { IconBoard() }

    private companion object {
        const val STATES_PER_PART = 12
    }
}
