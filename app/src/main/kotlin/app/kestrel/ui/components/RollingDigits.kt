package app.kestrel.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.tabular

/**
 * A ticking figure where only the characters that change roll (DESIGN 2.10, split-flap digits without tiles): the new
 * character slides in from -50 % of its cell while the old one leaves to +50 %, each cell clipped.
 * Slide on fastSpatialSpec, fade on fastEffectsSpec; with reduced motion the characters swap.
 *
 * Cells are matched from the end, so units and separators stay put when the figure gets shorter ("10 s" to "9 s").
 * The characters are hidden from TalkBack; give the sentence to speak in [contentDescription] (rule MF47), or leave
 * it null when an enclosing node speaks for the countdown.
 */
@Composable
fun RollingDigits(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    contentDescription: String? = null,
) {
    val reduceMotion = KestrelTheme.reduceMotion
    val slide = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val figureStyle = style.tabular()
    val cells = tabularText(text, wraps = false)
    Row(
        modifier.clearAndSetSemantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
    ) {
        cells.forEachIndexed { index, character ->
            val positionFromEnd = cells.length - index
            key(positionFromEnd) {
                AnimatedContent(
                    targetState = character,
                    // Cells share a baseline: a cell holding a digit gets a shorter line box than one holding a
                    // letter, so top-aligned cells would set the units of "13 m 07 s" low.
                    modifier = Modifier.alignByBaseline().clipToBounds(),
                    transitionSpec = {
                        if (reduceMotion) {
                            ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                        } else {
                            (slideInVertically(slide) { -it / 2 } + fadeIn(fade)) togetherWith
                                (slideOutVertically(slide) { it / 2 } + fadeOut(fade)) using
                                SizeTransform(clip = true)
                        }
                    },
                    label = "digit $positionFromEnd",
                ) { shown ->
                    Text(text = shown.toString(), style = figureStyle, color = color, maxLines = 1)
                }
            }
        }
    }
}
