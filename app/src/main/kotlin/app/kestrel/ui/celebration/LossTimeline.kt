package app.kestrel.ui.celebration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The loss as pure geometry (DESIGN 3.22). The kestrel hovers and stoops exactly as for a win, but a gust snatches the
 * lot's tag out of the grass just before the strike, so the talons close on nothing. It flies back to its post with
 * nothing, turns to watch the tag go, and hunches as clouds close over the sun and the rain comes. It shakes the rain
 * off, one feather drifts down, and it gives one low "kee". Every moment is a function of the time.
 */
internal object LossTimeline {
    // The miss.
    const val GUST_FROM = 2_620L
    const val GUST_TO = 3_700L
    const val MISS = CelebrationTimeline.STRIKE
    const val PERCH = CelebrationTimeline.PERCH
    const val WINGS_FOLDED = 4_980L
    const val TURNED = 5_100L

    // The weather.
    const val CLOUDS_FROM = 3_100L
    const val CLOUDS_TO = 5_000L
    const val DIM_FROM = 3_300L
    const val DIM_TO = 5_600L
    const val RAIN_FROM = 4_500L
    const val RAIN_FULL = 5_700L

    // The bird's sorrow.
    const val SLUMP_FROM = 5_100L
    const val SLUMP_TO = 5_900L
    const val SHIVER_FROM = 5_950L
    const val SHIVER_TO = 6_350L
    const val FEATHER_FROM = 6_000L
    const val FEATHER_FALL = 1_200L
    const val CALL_AT = 6_450L
    const val CALL_MILLIS = 900L

    // The words under the scene.
    const val TITLE_FROM = 300L
    const val HEADLINE_FROM = 3_150L
    const val MONEY_FROM = 3_700L
    const val COMFORT_FROM = 6_300L
    const val END = CelebrationTimeline.END

    private fun progress(
        t: Long,
        from: Long,
        to: Long,
    ): Float = CelebrationTimeline.progress(t, from, to)

    /** The kestrel at [timeMillis]: the win's flight up to the post, then the turn, the slump, the shiver, the call. */
    fun bird(
        timeMillis: Long,
        size: Size,
    ): BirdPose {
        val t = timeMillis.coerceIn(0L, END)
        if (t < PERCH) return CelebrationTimeline.bird(t, size)
        val layout = SceneLayout(size)
        val fold = easeInOut(progress(t, PERCH, WINGS_FOLDED))
        val facing = lerp(-1f, 1f, easeInOut(progress(t, PERCH, TURNED)))
        val slump = easeInOut(progress(t, SLUMP_FROM, SLUMP_TO))
        val shiverProgress = progress(t, SHIVER_FROM, SHIVER_TO)
        val shiver =
            if (t in
                SHIVER_FROM..SHIVER_TO
            ) {
                6f * sin(2f * PI.toFloat() * 5f * shiverProgress) * (1f - shiverProgress)
            } else {
                0f
            }
        // The call lifts the beak a little, and lets it fall again.
        val call = sin(PI.toFloat() * progress(t, CALL_AT, CALL_AT + CALL_MILLIS))
        // Hunched a little, wings let down, the head sunk towards the grass where the tag was and the eyelids heavy.
        val pitch = lerp(-76f, -71f, slump)
        return BirdPose(
            position = layout.perchOrigin(facing, pitch),
            unit = layout.unit,
            facing = facing,
            pitch = pitch,
            wingAngle = lerp(-100f, -182f, fold) + shiver + 7f * slump,
            farWingAngle = lerp(-112f, -184f, fold) - shiver + 5f * slump,
            wingSpan = lerp(0.8f, 0.74f, fold),
            wingChord = lerp(1f, 0.72f, fold),
            tailFan = 1f - fold,
            legs = 1f,
            legAngle = -pitch,
            headTurn = -pitch + 44f * slump - 16f * call,
            fold = fold,
            eyelid = (0.9f * slump - 0.35f * call).coerceAtLeast(0f),
        )
    }

    /** The tag: in the grass, then lifted by the gust and tumbling out of the scene. Null once it has gone. */
    fun tag(
        timeMillis: Long,
        size: Size,
    ): BlownTag? {
        val t = timeMillis.coerceIn(0L, END)
        val layout = SceneLayout(size)
        if (t < GUST_FROM) return BlownTag(layout.tagRest, spin = 0f, lying = true)
        val p = progress(t, GUST_FROM, GUST_TO)
        if (p >= 1f) return null
        // It skips once on the grass, then rides the gust up and away to the right.
        val skip = 6f * layout.unit * sin(PI.toFloat() * (p / 0.3f).coerceAtMost(1f))
        val anchor =
            Offset(
                layout.tagRest.x + size.width * 0.48f * p,
                layout.tagRest.y - size.height * 0.24f * p * p - skip,
            )
        return BlownTag(anchor, spin = 560f * easeOut(p), lying = false)
    }

    /** How grey the day has turned, from 0 (the morning of the win) to 1. */
    fun dim(timeMillis: Long): Float = easeInOut(progress(timeMillis, DIM_FROM, DIM_TO))

    /** How far the rain clouds have come over the sun. */
    fun clouds(timeMillis: Long): Float = easeInOut(progress(timeMillis, CLOUDS_FROM, CLOUDS_TO))

    /** How hard it rains, from 0 to 1. */
    fun rain(timeMillis: Long): Float = easeIn(progress(timeMillis, RAIN_FROM, RAIN_FULL)).coerceAtMost(1f)

    /** The one feather the shiver shakes loose: from the bird's shoulder to the grass by the post. */
    fun feather(size: Size): Feather {
        val layout = SceneLayout(size)
        val u = layout.unit
        return Feather(
            start = layout.postTop + Offset(6f * u, -16f * u),
            rest = Offset(layout.postTop.x + 15f * u, size.height * 0.9f),
            from = FEATHER_FROM,
            fallMillis = FEATHER_FALL,
            phase = 0.6f,
            blue = false,
        )
    }

    /** How far the "kee" has gone (0 to 1), or null outside it. */
    fun call(timeMillis: Long): Float? =
        progress(timeMillis, CALL_AT, CALL_AT + CALL_MILLIS).takeIf { timeMillis >= CALL_AT && it < 1f }
}

/** The lot's tag in the loss: where it is, how far it has spun (degrees), and whether it still lies in the grass. */
internal data class BlownTag(
    val anchor: Offset,
    val spin: Float,
    val lying: Boolean,
)
