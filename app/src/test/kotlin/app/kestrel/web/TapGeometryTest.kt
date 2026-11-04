package app.kestrel.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TapGeometryTest {
    @Test
    fun `CSS pixels are multiplied by the device pixel ratio`() {
        // Pixel 9a: 1080 px wide at a ratio of 2.625 is 411.4 CSS px.
        val point = TapGeometry.toViewPixels(206.0, 484.0, 2.625, 1080, 2424)
        assertEquals(540.75f, point.x, 0.001f)
        assertEquals(1270.5f, point.y, 0.001f)
    }

    @Test
    fun `a ratio of one keeps the coordinates`() {
        val point = TapGeometry.toViewPixels(10.5, 20.25, 1.0, 100, 100)
        assertEquals(10.5f, point.x, 0.0001f)
        assertEquals(20.25f, point.y, 0.0001f)
    }

    @Test
    fun `the view edges are inclusive at zero and exclusive at the size`() {
        TapGeometry.toViewPixels(0.0, 0.0, 2.625, 1080, 2424)
        assertThrows(IllegalArgumentException::class.java) { TapGeometry.toViewPixels(411.5, 10.0, 2.625, 1080, 2424) }
        assertThrows(IllegalArgumentException::class.java) { TapGeometry.toViewPixels(10.0, 923.5, 2.625, 1080, 2424) }
    }

    @Test
    fun `points outside the view are refused rather than clamped`() {
        assertThrows(IllegalArgumentException::class.java) { TapGeometry.toViewPixels(-1.0, 10.0, 2.0, 1080, 2424) }
        assertThrows(IllegalArgumentException::class.java) { TapGeometry.toViewPixels(10.0, 5000.0, 2.0, 1080, 2424) }
    }

    @Test
    fun `implausible ratios and non-numbers are refused`() {
        listOf(0.0, -2.0, 0.2, 9.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { ratio ->
            assertThrows(
                IllegalArgumentException::class.java,
            ) { TapGeometry.toViewPixels(10.0, 10.0, ratio, 1080, 2424) }
        }
        assertThrows(
            IllegalArgumentException::class.java,
        ) { TapGeometry.toViewPixels(Double.NaN, 10.0, 2.0, 1080, 2424) }
    }

    @Test
    fun `a WebView without a size cannot be tapped`() {
        assertThrows(IllegalArgumentException::class.java) { TapGeometry.toViewPixels(1.0, 1.0, 2.0, 0, 0) }
    }
}
