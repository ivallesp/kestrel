package app.kestrel.ui.harness

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kestrel.ui.theme.Sans

/** System bar sizes of a Pixel 9a with gesture navigation (DESIGN 2.8). The app itself always reads WindowInsets. */
val StatusBarHeight = 40.dp
val GestureBarHeight = 24.dp

/** The insets every screenshot passes to its screen, as Android would on the phone. */
val PhoneInsets = WindowInsets(top = StatusBarHeight, bottom = GestureBarHeight)

/**
 * Draws the status bar (clock, Wi-Fi, signal, battery) and the gesture handle over a screen, the way Android composites
 * them over an edge-to-edge app. [lightIcons] is for dark theme and screens with a photograph at the top (S3).
 */
@Composable
fun DeviceChrome(
    time: String,
    lightIcons: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val iconColour = if (lightIcons) Color(0xFFF7F3EC) else Color(0xFF1E1B16)
    Box(modifier.fillMaxSize()) {
        content()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(StatusBarHeight)
                    .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = time,
                color = iconColour,
                style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                WifiGlyph(iconColour)
                SignalGlyph(iconColour)
                BatteryGlyph(iconColour)
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .width(108.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(iconColour.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun WifiGlyph(colour: Color) {
    Canvas(Modifier.size(16.dp)) {
        val centre = Offset(size.width / 2, size.height * 0.9f)
        val stroke = Stroke(width = 2.dp.toPx())
        for (ring in 1..2) {
            val radius = size.height * (0.3f + 0.3f * ring)
            drawArc(
                color = colour,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = Size(radius * 2, radius * 2),
                style = stroke,
            )
        }
        drawCircle(colour, radius = 2.dp.toPx(), center = Offset(centre.x, centre.y - 2.dp.toPx()))
    }
}

@Composable
private fun SignalGlyph(colour: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 14.dp)) {
        val bar = size.width / 5.5f
        for (index in 0..3) {
            val height = size.height * (0.4f + 0.2f * index)
            drawRoundRect(
                color = colour,
                topLeft = Offset(index * bar * 1.45f, size.height - height),
                size = Size(bar, height),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun BatteryGlyph(colour: Color) {
    Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
        drawRoundRect(
            colour,
            size = Size(size.width - 2.dp.toPx(), size.height),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        drawRoundRect(
            color = colour,
            topLeft = Offset(size.width - 2.dp.toPx(), size.height * 0.3f),
            size = Size(2.dp.toPx(), size.height * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}
