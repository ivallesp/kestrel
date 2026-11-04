package app.kestrel.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.R
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.theme.LightStatusPalette
import app.kestrel.ui.theme.Spacing

private val Squircle = RoundedCornerShape(percent = 32)
private val RoundedSquare = RoundedCornerShape(percent = 22)

/** Material You themed-icon colours a Pixel picks from a neutral wallpaper (light and dark launcher). */
private val ThemedLight = Color(0xFFDCE3EC) to Color(0xFF26313D)
private val ThemedDark = Color(0xFF2A3139) to Color(0xFFC7D2DE)

/**
 * S7 (DESIGN 4): the launcher icon under the masks launchers use, its three layers with the 66 dp safe zone, the
 * themed monochrome variant on light and dark home screens, and the small sizes with the notification icon in a
 * status-bar strip next to a Live Update chip.
 */
@Composable
fun IconBoard(modifier: Modifier = Modifier) {
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(PhoneInsets)
                .padding(horizontal = Spacing.ColumnA),
        ) {
            Spacer(Modifier.height(12.dp))
            SmallCaps("Launcher icon · adaptive")
            Text(
                "Kestrel",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text("A kestrel watching, not a target.", style = MaterialTheme.typography.bodyLarge, color = soft)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AdaptiveIcon(size = 200.dp) }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Labelled("Circle") { AdaptiveIcon(64.dp) }
                Labelled("Squircle") { AdaptiveIcon(64.dp, shape = Squircle) }
                Labelled("Rounded square") { AdaptiveIcon(64.dp, shape = RoundedSquare) }
                Labelled("Cookie") { AdaptiveIcon(64.dp, shape = MaterialShapes.Cookie12Sided.toShape()) }
            }
            Spacer(Modifier.height(16.dp))
            SmallCaps("Layers and safe zone")
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Labelled("Background") { LayerTile(R.drawable.ic_launcher_background) }
                Labelled("Foreground") { LayerTile(R.drawable.ic_launcher_foreground) }
                Labelled("Monochrome") {
                    LayerTile(R.drawable.ic_launcher_monochrome, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(16.dp))
            SmallCaps("Themed icons")
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeScreenStrip(
                    wallpaper = Brush.linearGradient(listOf(Color(0xFFE8E2D6), Color(0xFFC9D3DC))),
                    label = Color(0xFF1E1B16),
                    themed = ThemedLight,
                    modifier = Modifier.weight(1f),
                )
                HomeScreenStrip(
                    wallpaper = Brush.linearGradient(listOf(Color(0xFF1B2229), Color(0xFF3A3328))),
                    label = Color(0xFFEDE6D9),
                    themed = ThemedDark,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            SmallCaps("Small sizes")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AdaptiveIcon(48.dp)
                AdaptiveIcon(36.dp)
                AdaptiveIcon(24.dp)
                StatusBarStrip()
            }
        }
    }
}

/** An adaptive icon as a launcher draws it: the 108 dp layers with the centre 72 dp filling [size], then masked. */
@Composable
private fun AdaptiveIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    themed: Pair<Color, Color>? = null,
) {
    Box(modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (themed != null) {
            Box(Modifier.fillMaxSize().background(themed.first))
            Image(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                colorFilter = ColorFilter.tint(themed.second),
                modifier = Modifier.requiredSize(size * 1.5f),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.requiredSize(size * 1.5f),
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(size * 1.5f),
            )
        }
    }
}

/** A whole 108 dp layer with the 66 dp safe zone dashed in the outline colour. */
@Composable
private fun LayerTile(
    layer: Int,
    tint: Color? = null,
) {
    val guide = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .size(108.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Image(
            painter = painterResource(layer),
            contentDescription = null,
            colorFilter = tint?.let { ColorFilter.tint(it) },
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = guide,
                radius = size.width * 33f / 108f,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
            )
        }
    }
}

@Composable
private fun Labelled(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeScreenStrip(
    wallpaper: Brush,
    label: Color,
    themed: Pair<Color, Color>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(wallpaper)
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AdaptiveIcon(52.dp)
            Spacer(Modifier.height(4.dp))
            Text("Kestrel", style = MaterialTheme.typography.labelMedium, color = label)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AdaptiveIcon(52.dp, themed = themed)
            Spacer(Modifier.height(4.dp))
            Text("Themed", style = MaterialTheme.typography.labelMedium, color = label)
        }
    }
}

/** The status bar as a Live Update shows it: the notification icon and a countdown chip on the Bidding accent
 * the notifications use (setColor takes the light accent in both themes). */
@Composable
private fun StatusBarStrip() {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1B21))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("18:21", style = MaterialTheme.typography.labelLarge, color = Color(0xFFF2F0F7))
        Image(
            painter = painterResource(R.drawable.ic_notification),
            contentDescription = "Notification icon",
            modifier = Modifier.size(20.dp),
        )
        Row(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(LightStatusPalette.bidding.accent)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("0:36", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}
