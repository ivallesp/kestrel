package app.kestrel.ui.celebration

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loss's story as pure geometry, on a Pixel 9a-sized scene. */
class LossTimelineTest {
    private val size = Size(1078f, 1140f)
    private val layout = SceneLayout(size)
    private val unit = layout.unit

    @Test
    fun theFlightIsTheWinsUntilThePost() {
        var t = 0L
        while (t < LossTimeline.PERCH) {
            assertEquals("at $t ms", CelebrationTimeline.bird(t, size), LossTimeline.bird(t, size))
            t += 50L
        }
    }

    @Test
    fun theTalonsCloseOnEmptyGrassWhileTheTagBlowsAway() {
        val talons = LossTimeline.bird(LossTimeline.MISS, size).talons
        assertTrue("the strike lands where the tag lay", (talons - layout.tagRest).getDistance() < 2f * unit)
        val tag = LossTimeline.tag(LossTimeline.MISS, size)!!
        assertFalse(tag.lying)
        assertTrue("the tag is well away", (tag.anchor - talons).getDistance() > 20f * unit)
    }

    @Test
    fun theTagLiesStillUntilTheGustThenLeavesTheScene() {
        assertEquals(
            BlownTag(layout.tagRest, spin = 0f, lying = true),
            LossTimeline.tag(LossTimeline.GUST_FROM - 1L, size),
        )
        var x = layout.tagRest.x
        var t = LossTimeline.GUST_FROM + 20L
        while (t < LossTimeline.GUST_TO) {
            val tag = LossTimeline.tag(t, size)!!
            assertTrue("it only goes away, at $t ms", tag.anchor.x >= x)
            x = tag.anchor.x
            t += 20L
        }
        assertTrue("out of the scene before it vanishes", x > size.width * 0.95f)
        assertNull(LossTimeline.tag(LossTimeline.GUST_TO, size))
        assertNull(LossTimeline.tag(LossTimeline.END, size))
    }

    @Test
    fun theFeetStayOnThePostFromTheLandingToTheEnd() {
        var t = LossTimeline.PERCH
        while (t <= LossTimeline.END) {
            val talons = LossTimeline.bird(t, size).talons
            assertTrue("at $t ms", (talons - layout.postTop).getDistance() < 0.5f * unit)
            t += 20L
        }
    }

    @Test
    fun itHunchesInTheRainThenEndsUprightAndReady() {
        val landed = LossTimeline.bird(LossTimeline.PERCH, size)
        val rain = LossTimeline.bird(LossTimeline.RISE_FROM - 100L, size)
        assertTrue("the head has sunk in the rain", rain.headTurn > landed.headTurn + 30f)
        assertTrue(rain.eyelid > 0.5f)

        val end = LossTimeline.bird(LossTimeline.END, size)
        assertEquals(1f, end.facing)
        assertEquals("eyes wide open again", 0f, end.eyelid, 0.001f)
        assertTrue("head up, level with the body", kotlin.math.abs(end.headTurn + end.pitch) < 10f)
        assertEquals("wings folded after the stretch", 1f, end.fold, 0.001f)
        val stretching = LossTimeline.bird((LossTimeline.STRETCH_FROM + LossTimeline.STRETCH_TO) / 2, size)
        assertTrue("a wing opens for the stretch", stretching.fold < 0.5f)
    }

    @Test
    fun nothingMovesAfterTheEnd() {
        assertEquals(LossTimeline.bird(LossTimeline.END, size), LossTimeline.bird(LossTimeline.END + 10_000L, size))
        assertEquals(LossTimeline.rain(LossTimeline.END), LossTimeline.rain(LossTimeline.END + 10_000L))
    }

    @Test
    fun theWeatherClosesInThenTheSunComesBack() {
        assertEquals(0f, LossTimeline.dim(0L))
        assertEquals("grey at its worst", 1f, LossTimeline.dim(LossTimeline.SUN_FROM - 1L))
        assertEquals("the sun is back", 0f, LossTimeline.dim(LossTimeline.END))
        assertEquals(1f, LossTimeline.sun(LossTimeline.END))
        assertEquals(1f, LossTimeline.parting(LossTimeline.END))
        assertEquals(1f, LossTimeline.rainbow(LossTimeline.END))
        assertEquals(0f, LossTimeline.rain(LossTimeline.RAIN_FROM - 1L))
        assertEquals(1f, LossTimeline.rain(LossTimeline.RAIN_EASE_FROM))
        assertEquals(0f, LossTimeline.rain(LossTimeline.RAIN_EASE_TO))
        assertEquals(0f, LossTimeline.rain(LossTimeline.END))
        // The rain sets in steadily and eases off steadily, before the sun.
        var last = 0f
        var t = 0L
        while (t <= LossTimeline.RAIN_EASE_FROM) {
            val rain = LossTimeline.rain(t)
            assertTrue("sets in, at $t ms", rain >= last)
            last = rain
            t += 50L
        }
        while (t <= LossTimeline.END) {
            val rain = LossTimeline.rain(t)
            assertTrue("eases, at $t ms", rain <= last)
            last = rain
            t += 50L
        }
        assertTrue(LossTimeline.RAIN_EASE_TO <= LossTimeline.SUN_TO)
    }

    @Test
    fun aFewFlowersOpenInTheSunBeforeTheEnd() {
        val flowers = LossTimeline.flowers(size)
        assertTrue(flowers.size in 3..8)
        for (flower in flowers) {
            assertTrue(flower.bloomAt >= LossTimeline.SUN_FROM)
            assertTrue(flower.bloomAt + CelebrationTimeline.BLOOM_MILLIS <= LossTimeline.END)
            assertTrue("by the post, clear of the rainbow", flower.at.x < size.width * 0.5f)
        }
    }

    @Test
    fun theFeatherComesToRestInTheGrassBeforeTheEnd() {
        val feather = LossTimeline.feather(size)
        assertNull(feather.at(LossTimeline.FEATHER_FROM - 1L))
        assertTrue(feather.from + feather.fallMillis <= LossTimeline.END)
        assertTrue((feather.at(LossTimeline.END)!!.first - feather.rest).getDistance() < 0.01f)
    }

    @Test
    fun theBirdStaysInTheScene() {
        var t = 0L
        while (t <= LossTimeline.END) {
            val bird = LossTimeline.bird(t, size)
            for (point in listOf(bird.position, bird.talons, bird.toScene(KestrelShape.HeadCentre))) {
                assertTrue("x at $t ms", point.x in 0f..size.width)
                assertTrue("y at $t ms", point.y in 0f..size.height)
            }
            t += 10L
        }
    }

    @Test
    fun theWordsAndTheCallComeAfterTheirMoments() {
        assertTrue(LossTimeline.HEADLINE_FROM > LossTimeline.MISS)
        assertTrue(LossTimeline.MONEY_FROM > LossTimeline.HEADLINE_FROM)
        assertTrue(LossTimeline.COMFORT_FROM >= LossTimeline.RAIN_FROM)
        assertTrue(LossTimeline.COMFORT_FROM + 600L <= LossTimeline.END)
        assertTrue(LossTimeline.CALL_AT + LossTimeline.CALL_MILLIS <= LossTimeline.SUN_FROM)
        assertNull(LossTimeline.call(LossTimeline.END))
        assertTrue(LossTimeline.HOPE_FROM >= LossTimeline.SUN_FROM)
        assertTrue(LossTimeline.HOPE_FROM + 600L <= LossTimeline.END)
        for (at in LossTimeline.READY_CALLS) assertTrue(at + LossTimeline.READY_CALL_MILLIS <= LossTimeline.END)
        assertTrue(LossTimeline.readyCalls(LossTimeline.END).all { it == null })
    }
}
