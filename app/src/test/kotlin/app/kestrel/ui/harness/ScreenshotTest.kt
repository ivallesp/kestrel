package app.kestrel.ui.harness

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.components.LocalLotPhotoLoader
import app.kestrel.ui.components.LocalSheetInsets
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The base of every screenshot test: a Pixel 9a (411 x 923 dp) in Robolectric's native graphics, the app's own fonts
 * and theme, fixture photographs, the phone's status bar and gesture handle, and a frozen clock.
 *
 * Record with `./gradlew recordRoborazziDebug`; the PNGs go to app/src/test/screenshots/ and are committed. A plain
 * `testDebugUnitTest` runs the same code without writing images.
 *
 * ```
 * class HomeScreenshots : ScreenshotTest() {
 *     @Test fun homeLight() = capture("S1-home", darkTheme = false, time = "18:08") {
 *         HomeScreen(HomeFixtures.home, windowInsets = PhoneInsets, onOpen = {})
 *     }
 * }
 * ```
 *
 * Screens get their data from fixtures in test sources (ui/fixture) and draw under [PhoneInsets]; they never read
 * the system clock. Infinite animations (the final-minute wave, LoadingIndicator) are frozen at [advanceMillis].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
abstract class ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Renders [content] and records `src/test/screenshots/<name>-light.png` or `-dark.png` (plus `-font130` at font
     * scale 1.3). [time] is the status-bar clock; [lightIcons] defaults to the theme (pass true over a photograph).
     * [online] false draws the status bar without Wi-Fi or signal, for the offline states.
     * [wholeScreen] captures every window, for bottom sheets, menus and dialogs.
     */
    protected fun capture(
        name: String,
        darkTheme: Boolean,
        time: String,
        lightIcons: Boolean = darkTheme,
        reduceMotion: Boolean = false,
        fontScale: Float = 1f,
        advanceMillis: Long = DEFAULT_ADVANCE_MILLIS,
        wholeScreen: Boolean = false,
        online: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ScreenshotSurface(
                darkTheme = darkTheme,
                time = time,
                lightIcons = lightIcons,
                reduceMotion = reduceMotion,
                fontScale = fontScale,
                online = online,
                content = content,
            )
        }
        composeRule.mainClock.advanceTimeBy(advanceMillis)
        val path = "src/test/screenshots/${fileName(name, darkTheme, fontScale)}.png"
        if (wholeScreen) captureScreenRoboImage(path) else composeRule.onRoot().captureRoboImage(path)
    }

    companion object {
        /** Long enough for enter animations to settle, short enough to keep the wave mid-cycle. */
        const val DEFAULT_ADVANCE_MILLIS = 1_500L

        fun fileName(
            name: String,
            darkTheme: Boolean,
            fontScale: Float,
        ): String =
            buildString {
                append(name)
                append(if (darkTheme) "-dark" else "-light")
                if (fontScale != 1f) append("-font${(fontScale * 100).toInt()}")
            }
    }
}

/**
 * The theme, fixture photos, density and device chrome a screenshot draws in. Also usable in UI tests. Sheets get the
 * phone's navigation-bar inset, which Robolectric's sheet windows lack.
 */
@Composable
fun ScreenshotSurface(
    darkTheme: Boolean,
    time: String,
    lightIcons: Boolean = darkTheme,
    reduceMotion: Boolean = false,
    fontScale: Float = 1f,
    online: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalLotPhotoLoader provides FixturePhotos,
        LocalDensity provides Density(density.density, fontScale),
        LocalSheetInsets provides WindowInsets(bottom = GestureBarHeight),
    ) {
        KestrelTheme(darkTheme = darkTheme, reduceMotion = reduceMotion) {
            DeviceChrome(
                time = time,
                lightIcons = lightIcons,
                lightHandle = darkTheme,
                online = online,
                content = content,
            )
        }
    }
}
