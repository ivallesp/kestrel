package app.kestrel.web

/** A point in the WebView's own pixels, where a MotionEvent is delivered. */
data class ViewPoint(
    val x: Float,
    val y: Float,
)

/**
 * Converts the CSS-pixel points kestrel.js reports (already relative to the visual viewport) into WebView pixels.
 * A tap outside the view could only land somewhere unintended, so it is refused rather than clamped.
 */
object TapGeometry {
    private const val MIN_PIXEL_RATIO = 0.5
    private const val MAX_PIXEL_RATIO = 8.0

    fun toViewPixels(
        xCssPx: Double,
        yCssPx: Double,
        devicePixelRatio: Double,
        viewWidthPx: Int,
        viewHeightPx: Int,
    ): ViewPoint {
        require(devicePixelRatio.isFinite() && devicePixelRatio in MIN_PIXEL_RATIO..MAX_PIXEL_RATIO) {
            "Device pixel ratio $devicePixelRatio is not plausible"
        }
        require(xCssPx.isFinite() && yCssPx.isFinite()) { "Tap point ($xCssPx, $yCssPx) is not a number" }
        require(viewWidthPx > 0 && viewHeightPx > 0) { "The WebView has no size (${viewWidthPx}x$viewHeightPx)" }
        val x = xCssPx * devicePixelRatio
        val y = yCssPx * devicePixelRatio
        require(x >= 0.0 && y >= 0.0 && x < viewWidthPx && y < viewHeightPx) {
            "Tap point ($x, $y) is outside the ${viewWidthPx}x$viewHeightPx view"
        }
        return ViewPoint(x.toFloat(), y.toFloat())
    }
}
