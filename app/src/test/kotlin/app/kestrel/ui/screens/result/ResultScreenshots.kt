package app.kestrel.ui.screens.result

import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import app.kestrel.ui.screens.lot.LotFixtures
import app.kestrel.ui.screens.lot.LotFixtures.resultState
import org.junit.Test

/** S4 in every kind of result (DESIGN S4, Q30), the won result in both themes and at large text. */
class ResultScreenshots : ScreenshotTest() {
    private val fixture = CatalogueFixture

    private fun result(
        name: String,
        moment: LotMoment,
        time: String,
        darkTheme: Boolean = false,
        reduceMotion: Boolean = false,
        fontScale: Float = 1f,
    ) = capture(name, darkTheme = darkTheme, time = time, reduceMotion = reduceMotion, fontScale = fontScale) {
        ResultScreen(moment.resultState(), windowInsets = PhoneInsets)
    }

    @Test
    fun wonLight() = result("S4-won", fixture.canonWon, "18:22")

    @Test
    fun wonDark() = result("S4-won", fixture.canonWon, "18:22", darkTheme = true)

    @Test
    fun wonLargeText() = result("S4-won", fixture.canonWon, "18:22", fontScale = 1.3f)

    @Test
    fun wonReducedMotion() = result("S4-won-reduced-motion", fixture.canonWon, "18:22", reduceMotion = true)

    @Test
    fun lostLight() = result("S4-lost", fixture.nintendo64Lost, "19:42")

    @Test
    fun lostDark() = result("S4-lost", fixture.nintendo64Lost, "19:42", darkTheme = true)

    @Test
    fun bidNotSentLight() = result("S4-bid-not-sent", fixture.canonBidNotSent, "18:22")

    @Test
    fun bidNotSentDark() = result("S4-bid-not-sent", fixture.canonBidNotSent, "18:22", darkTheme = true)

    @Test
    fun bidFailedLight() = result("S4-bid-failed", fixture.canonBidFailed, "18:22")

    @Test
    fun endedEarlyLight() = result("S4-ended-early", fixture.gameBoyEndedEarly, "09:31")

    @Test
    fun cancelledLight() = result("S4-cancelled", fixture.gameBoyCancelled, "18:08")

    @Test
    fun cancelledByGroupLight() = result("S4-cancelled-by-group", LotFixtures.walkmanSkippedByGroup, "20:12")

    @Test
    fun unknownLight() = result("S4-unknown", LotFixtures.canonResultUnknown, "18:22")
}
