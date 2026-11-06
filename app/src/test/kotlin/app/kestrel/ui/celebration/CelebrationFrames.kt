package app.kestrel.ui.celebration

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotSurface
import app.kestrel.ui.screens.lot.LotFixtures.resultState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Writes frames of the win celebration as PNGs, for looking at the animation and making a preview of it. Runs only
 * when the environment variable `KESTREL_FRAMES` names a folder, so a normal test run writes nothing:
 * `KESTREL_FRAMES=/tmp/frames ./gradlew :app:testDebugUnitTest --tests '*CelebrationFrames*'`
 * (`KESTREL_FRAMES_DARK=true`, `_STEP=66`, `_FROM`, `_TO`, `_FONT=2.0` and `_PREVIEW=true` vary it).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class CelebrationFrames {
    @get:Rule
    val composeRule = createComposeRule()

    private val folder: String? = System.getenv("KESTREL_FRAMES")?.takeIf { it.isNotBlank() }

    @Test
    fun writeFrames() {
        assumeTrue(folder != null)
        val out = File(folder!!).apply { mkdirs() }
        val dark = System.getenv("KESTREL_FRAMES_DARK") == "true"
        val step = System.getenv("KESTREL_FRAMES_STEP")?.toLongOrNull() ?: 500L
        val preview = System.getenv("KESTREL_FRAMES_PREVIEW") == "true"
        val from = System.getenv("KESTREL_FRAMES_FROM")?.toLongOrNull() ?: 0L
        val to = System.getenv("KESTREL_FRAMES_TO")?.toLongOrNull() ?: CelebrationTimeline.END
        val fontScale = System.getenv("KESTREL_FRAMES_FONT")?.toFloatOrNull() ?: 1f
        var frame by mutableLongStateOf(0L)
        composeRule.setContent {
            ScreenshotSurface(darkTheme = dark, time = "18:22", fontScale = fontScale) {
                WinCelebration(
                    content = if (preview) CelebrationContent.Preview else SAMPLE,
                    onDone = {},
                    windowInsets = PhoneInsets,
                    frameMillis = frame,
                )
            }
        }
        var time = from
        var index = 0
        while (time <= to) {
            frame = time
            composeRule.waitForIdle()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            File(out, "frame-%03d-%05d.png".format(index, time)).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            index++
            time += step
        }
    }

    private companion object {
        val SAMPLE = CelebrationContent.of(CatalogueFixture.canonWon.resultState())
    }
}
