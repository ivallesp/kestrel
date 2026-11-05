package app.kestrel.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The theme choice once showed "✓ Syst" on a phone with larger text: every label of a connected toggle row must be
 * shown whole, at any text size, in the narrowest row the app uses.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class ConnectedToggleRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val options = listOf("System", "Light", "Dark")

    private var width by mutableStateOf(347.dp)
    private var fontScale by mutableFloatStateOf(1f)
    private var selected by mutableIntStateOf(0)

    @Test
    fun noLabelIsCutAtAnyTextSizeOrRowWidth() {
        render()
        for (rowWidth in listOf(347.dp, 300.dp)) {
            for (scale in listOf(1f, 1.15f, 1.3f, 1.6f, 2f)) {
                for (choice in options.indices) {
                    width = rowWidth
                    fontScale = scale
                    selected = choice
                    composeRule.waitForIdle()
                    for (option in options) assertWhole(option, "width $rowWidth, text x$scale, selected $choice")
                }
            }
        }
    }

    @Test
    fun narrowRowAtLargeTextRendersWhole() {
        width = 300.dp
        fontScale = 1.6f
        render()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/toggle-row-narrow-light-font160.png")
    }

    private fun render() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                KestrelTheme(darkTheme = false) {
                    Box(Modifier.width(width)) {
                        ConnectedToggleRow(options = options, selectedIndex = selected, onSelect = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertWhole(
        option: String,
        case: String,
    ) {
        val node = composeRule.onAllNodesWithText(option).fetchSemanticsNodes().single()
        val results = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(results)
        val layout = results.single()
        assertFalse("\"$option\" is cut ($case)", layout.hasVisualOverflow)
    }
}
