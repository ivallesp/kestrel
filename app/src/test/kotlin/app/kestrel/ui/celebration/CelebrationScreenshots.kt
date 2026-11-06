package app.kestrel.ui.celebration

import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import app.kestrel.ui.screens.lot.LotFixtures.resultState
import org.junit.Test

/** The win celebration at its key moments, its last frame in both themes and at large text, and the preview. */
class CelebrationScreenshots : ScreenshotTest() {
    private val won = CelebrationContent.of(CatalogueFixture.canonWon.resultState())

    private fun frame(
        name: String,
        timeMillis: Long,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        content: CelebrationContent = won,
    ) = capture(name, darkTheme = darkTheme, time = "18:22", fontScale = fontScale) {
        WinCelebration(content, onDone = {}, windowInsets = PhoneInsets, frameMillis = timeMillis)
    }

    @Test
    fun hoverLight() = frame("celebration-hover", 900L)

    @Test
    fun stoopLight() = frame("celebration-stoop", 2_800L)

    @Test
    fun carryLight() = frame("celebration-carry", 3_900L)

    @Test
    fun mantleLight() = frame("celebration-mantle", 5_400L)

    @Test
    fun mantleDark() = frame("celebration-mantle", 5_400L, darkTheme = true)

    @Test
    fun endLight() = frame("celebration-end", CelebrationTimeline.END)

    @Test
    fun endDark() = frame("celebration-end", CelebrationTimeline.END, darkTheme = true)

    @Test
    fun endLargeText() = frame("celebration-end", CelebrationTimeline.END, fontScale = 1.3f)

    @Test
    fun previewLight() = frame("celebration-preview", CelebrationTimeline.END, content = CelebrationContent.Preview)

    @Test
    fun previewDark() =
        frame("celebration-preview", CelebrationTimeline.END, darkTheme = true, content = CelebrationContent.Preview)
}
