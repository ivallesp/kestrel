package app.kestrel.ui.celebration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The celebration's track (res/raw/celebration.wav, made by tools/sound/celebration_sound.py) against the scene's
 * timeline: same length, the thump on the strike, and a high "kee" at each call.
 */
class CelebrationTrackTest {
    private val samples: FloatArray
    private val rate: Int

    init {
        val bytes = File("src/main/res/raw/celebration.wav").readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        rate = header.getInt(24)
        assertEquals("mono", 1, header.getShort(22).toInt())
        assertEquals("16-bit", 16, header.getShort(34).toInt())
        // The data chunk follows the 44-byte canonical header the generator writes.
        val data = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        samples = FloatArray(data.remaining()) { data.get(it) / 32768f }
    }

    private fun rms(
        fromSeconds: Double,
        toSeconds: Double,
    ): Double {
        val a = (fromSeconds * rate).toInt()
        val b = (toSeconds * rate).toInt()
        var sum = 0.0
        for (i in a until b) sum += samples[i] * samples[i]
        return sqrt(sum / (b - a))
    }

    /** Power at [hertz] over the window (Goertzel). */
    private fun power(
        hertz: Double,
        fromSeconds: Double,
        toSeconds: Double,
    ): Double {
        val coefficient = 2 * cos(2 * PI * hertz / rate)
        var previous = 0.0
        var before = 0.0
        for (i in (fromSeconds * rate).toInt() until (toSeconds * rate).toInt()) {
            val next = samples[i] + coefficient * previous - before
            before = previous
            previous = next
        }
        return previous * previous + before * before - coefficient * previous * before
    }

    @Test
    fun theTrackLastsAsLongAsTheScene() {
        val seconds = samples.size.toDouble() / rate
        assertEquals(CelebrationTimeline.END / 1000.0, seconds, 0.01)
        assertTrue("never clips", samples.maxOf { abs(it) } < 0.99f)
    }

    @Test
    fun theThumpLandsOnTheStrike() {
        // The loudest 20 ms between the stoop and the landing starts within 60 ms of the strike.
        val strike = CelebrationTimeline.STRIKE / 1000.0
        val loudest = (250 until 450).map { it / 100.0 }.maxBy { rms(it, it + 0.02) }
        assertTrue("loudest at $loudest s, strike at $strike s", abs(loudest - strike) <= 0.06)
    }

    @Test
    fun eachCallIsAHighKeeAtItsMoment() {
        for (call in CelebrationTimeline.CALLS) {
            val at = call / 1000.0
            val during = power(3_000.0, at + 0.02, at + 0.1)
            val before = power(3_000.0, at - 0.1, at - 0.02)
            assertTrue("a call at $at s", during > 20 * before)
        }
    }
}
