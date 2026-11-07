package app.kestrel.ui.celebration

import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import app.kestrel.ui.screens.lot.LotFixtures.resultState
import org.junit.Test

/** The loss at its key moments, its last frame in both themes and at large text, and the preview. */
class LossScreenshots : ScreenshotTest() {
    private val lost = LossContent.of(CatalogueFixture.nintendo64Lost.resultState())

    private fun frame(
        name: String,
        timeMillis: Long,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        content: LossContent = lost,
    ) = capture(name, darkTheme = darkTheme, time = "19:42", fontScale = fontScale) {
        LossScene(content, onDone = {}, windowInsets = PhoneInsets, frameMillis = timeMillis)
    }

    @Test
    fun missLight() = frame("loss-miss", 3_150L)

    @Test
    fun rainLight() = frame("loss-rain", 5_400L)

    @Test
    fun endLight() = frame("loss-end", LossTimeline.END)

    @Test
    fun endDark() = frame("loss-end", LossTimeline.END, darkTheme = true)

    @Test
    fun endLargeText() = frame("loss-end", LossTimeline.END, fontScale = 1.3f)

    @Test
    fun previewLight() = frame("loss-preview", LossTimeline.END, content = LossContent.Preview)
}
