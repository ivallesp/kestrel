package app.kestrel.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.PhotoStyle
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.PlateShapes
import coil3.compose.rememberAsyncImagePainter

/** Turns a photo address into a painter. The app loads through Coil; screenshot tests decode fixture files. */
interface LotPhotoLoader {
    /** A painter for [url], or null to leave the plate empty (plain paper, never a skeleton). */
    @Composable
    fun painterFor(url: String): Painter?
}

/** Loads with the app's Coil image loader (KestrelApp), which keeps a disk cache the notifications reuse. */
object CoilLotPhotoLoader : LotPhotoLoader {
    @Composable
    override fun painterFor(url: String): Painter = rememberAsyncImagePainter(url)
}

val LocalLotPhotoLoader = staticCompositionLocalOf<LotPhotoLoader> { CoilLotPhotoLoader }

/**
 * The shared-element scopes of the screen transition the plate is in, provided by the app shell for each screen.
 * Null in screenshot tests and whenever there is no transition.
 */
@Immutable
class PlateTransition(
    val sharedScope: SharedTransitionScope,
    val animatedScope: AnimatedVisibilityScope,
)

val LocalPlateTransition = staticCompositionLocalOf<PlateTransition?> { null }

/** Lifts near-white studio backgrounds (from about 96 % up) to pure white before the plate multiply. */
private val LiftWhites =
    ColorMatrix(
        floatArrayOf(
            1.04f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1.04f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1.04f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        ),
    )

/**
 * A lot photograph printed on a paper plate (DESIGN 2.4).
 *
 * - Cutouts: whites lifted by 1.04, drawn in an offscreen layer and multiplied onto the plate colour, so the seller's
 *   white becomes the plate; fitted with [cutoutInset] in both themes.
 * - Scenes: cropped to fill, biased by the photo's focus; multiplied in light theme, at 88 % brightness in dark.
 *
 * Sizes and corners (DESIGN 2.4): rows 72 dp with 10 dp corners and an 8 dp inset; the S2 header 104 dp with 12 dp;
 * the hero 379 x 128 dp with [PlateShapes.Hero]; S4 379 x 176 dp (won) or 136 dp (lost) with 20 dp corners and an
 * 18 dp inset; sheets 40 dp with 8 dp corners.
 *
 * [sharedElementKey] ("plate-<id>") joins the plate of the list and the plate of the detail screen in the screen
 * transition, clipped to [shape]. Reduced motion cuts instead.
 */
@Composable
fun LotPlate(
    photo: LotPhoto?,
    modifier: Modifier = Modifier,
    shape: Shape = PlateShapes.Row,
    cutoutInset: Dp = 8.dp,
    contentDescription: String? = null,
    sharedElementKey: String? = null,
) {
    val catalogue = KestrelTheme.catalogue
    val plate = catalogue.plate
    val dark = KestrelTheme.isDark
    val transition = LocalPlateTransition.current
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val shared =
        if (sharedElementKey != null && transition != null && !KestrelTheme.reduceMotion) {
            with(transition.sharedScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(sharedElementKey),
                    animatedVisibilityScope = transition.animatedScope,
                    boundsTransform = BoundsTransform { _, _ -> spatial },
                    clipInOverlayDuringTransition = OverlayClip(shape),
                )
            }
        } else {
            Modifier
        }
    Box(
        modifier
            .then(shared)
            .clip(shape)
            .background(plate),
    ) {
        val url = photo?.url ?: return@Box
        val painter = LocalLotPhotoLoader.current.painterFor(url) ?: return@Box
        val cutout = photo.style == PhotoStyle.Cutout
        val multiply = cutout || !dark
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(if (cutout) cutoutInset else 0.dp)
                    .then(
                        if (multiply) {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(plate, blendMode = BlendMode.Multiply)
                                }
                        } else {
                            Modifier
                        },
                    ),
            contentScale = if (cutout) ContentScale.Fit else ContentScale.Crop,
            alignment = BiasAlignment(photo.focusX, photo.focusY),
            colorFilter =
                when {
                    cutout -> ColorFilter.colorMatrix(LiftWhites)
                    dark -> ColorFilter.tint(catalogue.scenePhotoDim, BlendMode.Multiply)
                    else -> null
                },
        )
    }
}
