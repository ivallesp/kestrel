package app.kestrel.ui.screens.lot

import app.kestrel.ui.fixture.CatalogueFixture
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the lot screens tick, and the small copy helpers they share. */
class LotTicksAndCopyTest {
    private val end = CatalogueFixture.millis("2025-11-09T18:21:40Z")

    private fun delayAt(time: String) = LotTicks.delayMillis(CatalogueFixture.millis(time), end, finished = false)

    @Test
    fun theFinalMinuteTicksEveryTenthOfASecond() {
        assertEquals(100L, delayAt("2025-11-09T18:21:04.250Z"))
        assertEquals(100L, delayAt("2025-11-09T18:20:40Z"))
        assertEquals(100L, delayAt("2025-11-09T18:21:39.950Z"))
    }

    @Test
    fun afterTheEndItTicksOnTheSecondUntilTheResult() {
        assertEquals(1_000L, delayAt("2025-11-09T18:21:40Z"))
        assertEquals(500L, delayAt("2025-11-09T18:21:45.500Z"))
    }

    @Test
    fun theFinalHourTicksOnTheSecond() {
        assertEquals(750L, delayAt("2025-11-09T18:08:33.250Z"))
        assertEquals(1_000L, delayAt("2025-11-09T18:08:33Z"))
        // Never past the start of the final minute.
        assertEquals(300L, delayAt("2025-11-09T18:20:39.700Z"))
    }

    @Test
    fun furtherOutItTicksOnTheMinute() {
        assertEquals(27_000L, delayAt("2025-11-09T16:00:33Z"))
        // Never past the start of the final hour.
        assertEquals(10_000L, delayAt("2025-11-09T17:21:30Z"))
    }

    @Test
    fun aFinishedLotTicksOnTheMinute() {
        assertEquals(
            27_000L,
            LotTicks.delayMillis(CatalogueFixture.millis("2025-11-09T18:22:33Z"), end, finished = true),
        )
    }

    @Test
    fun shortTitlesKeepTwoRealWords() {
        assertEquals("Canon AE-1", shortTitle("Canon AE-1 Program + 50mm f/1.8"))
        assertEquals("Omega Speedmaster", shortTitle("Omega Speedmaster “Pre-moon” 1960s"))
        assertEquals("Game & Watch", shortTitle("Game & Watch Donkey Kong II"))
        assertEquals("Nintendo 64", shortTitle("Nintendo 64 console"))
    }

    @Test
    fun unitsAreSpokenInWords() {
        assertEquals("6.1 seconds before the end", speakUnits("6.1\u202Fs before the end"))
        assertEquals("1 second, 10 minutes, 2 hours", speakUnits("1\u202Fs, 10\u202Fmin, 2\u202Fh"))
    }

    @Test
    fun currencySymbolsFollowEbaysFormat() {
        assertEquals("£", currencySymbol("GBP"))
        assertEquals("US$", currencySymbol("USD"))
        assertEquals("€", currencySymbol("EUR"))
    }
}
