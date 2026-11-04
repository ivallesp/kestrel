package app.kestrel.ui.model

import androidx.compose.runtime.Immutable
import app.kestrel.model.Snipe

/**
 * How a lot photograph sits on its paper plate (DESIGN 2.4).
 *
 * - [Cutout]: studio shots on white. Fitted with an inset and multiplied onto the plate, so the white becomes paper.
 * - [Scene]: photos with their own background. Cropped to fill the plate; multiplied in light theme, drawn at 88 %
 *   brightness in dark theme.
 */
enum class PhotoStyle { Cutout, Scene }

/**
 * A lot photograph: [url] is what the image loader fetches (eBay's image address in the app, a fixture key in tests).
 * [focusX] and [focusY] (from -1 to 1) bias the crop of a [PhotoStyle.Scene] photo.
 */
@Immutable
data class LotPhoto(
    val url: String?,
    val style: PhotoStyle = PhotoStyle.Cutout,
    val focusX: Float = 0f,
    val focusY: Float = 0f,
) {
    companion object {
        /**
         * The photo of a snipe. Kestrel cannot yet tell a studio shot from a scene, so listing photos use the cutout
         * treatment: it never crops the item and never glares in dark theme.
         */
        fun of(snipe: Snipe): LotPhoto = LotPhoto(url = snipe.imageUrl)
    }
}
