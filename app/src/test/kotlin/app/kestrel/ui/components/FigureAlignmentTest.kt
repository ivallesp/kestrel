package app.kestrel.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Side-by-side labels and figures share a baseline (DESIGN Q11). A line of digits gets a shorter box than a line of
 * words, so only baseline alignment keeps them level; real font metrics need the native graphics mode.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class FigureAlignmentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aLedgerValueSharesItsLabelsBaseline() {
        val rows = listOf("Final price" to "£101.00", "Postage" to "£6.50", "Total" to "£107.50")
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                Column {
                    rows.forEach { (label, value) -> LedgerRow(label, value, total = label == "Total") }
                }
            }
        }
        rows.forEach { (label, value) ->
            assertEquals("$label and $value", baselineOf(label), baselineOf(value), 1f)
        }
    }

    private fun baselineOf(text: String): Float {
        val node = composeRule.onNodeWithText(text).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return node.positionInRoot.y + layouts.single().firstBaseline
    }
}
