package app.kestrel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.kestrel.ui.theme.Spacing

/**
 * The fade above the gesture handle on every scrolling screen without a dock (DESIGN 2.8, rule MF12): the
 * bottom inset plus 28 dp, from transparent to the page colour, so text never runs under the handle at rest. Put it
 * last in the screen's Box. [windowInsets] are the screen's insets (the app passes the system ones, tests a fixed
 * phone frame).
 */
@Composable
fun BoxScope.BottomFade(
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
) {
    val bottomInset = windowInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val fade = Brush.verticalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.94f), color))
    Box(
        modifier =
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomInset + Spacing.BottomFadeHeight)
                .background(fade),
    )
}
