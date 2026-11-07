package app.kestrel.ui.celebration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The win's story, as a pure function of time so every frame can be drawn and tested on its own. A kestrel hangs in
 * the wind with its head perfectly still (the bird's own trick, and Kestrel's: wait, then strike at the last moment),
 * folds and stoops onto the lot's tag in the grass, carries it up, turns and lands on a fence post. Then the
 * celebration: it turns round, throws its wings up over its prize (a raptor's mantle) and calls "kee-kee-kee", the sun
 * breaks into rays, the meadow blooms from the post outwards and a few feathers drift down. It settles and looks
 * round. It plays once; nothing loops, and the last frame stays festive.
 */
internal object CelebrationTimeline {
    const val HOVER_FROM = 0L
    const val FOLD_FROM = 2_100L
    const val STOOP_FROM = 2_400L
    const val FLARE_FROM = 2_880L
    const val STRIKE = 3_050L
    const val CLIMB_FROM = 3_200L
    const val TURN_FROM = 3_650L
    const val TURN_TO = 3_850L
    const val GLIDE_FROM = 4_050L
    const val LAND_FROM = 4_380L
    const val PERCH = 4_620L
    const val TURN_ROUND_TO = 4_900L
    const val MANTLE_FROM = 4_900L
    const val MANTLE_PEAK = 5_250L
    const val MANTLE_HOLD_TO = 5_850L
    const val SETTLED = 6_350L
    const val LOOK_FROM = 6_550L
    const val LOOK_TO = 7_050L

    /** The festivities, timed to the mantle: the call, the sun's rays, the meadow's bloom and the feathers. */
    val CALLS = listOf(5_000L, 5_260L, 5_520L)
    const val CALL_MILLIS = 760L
    const val SUNBURST_FROM = 4_950L
    const val BLOOM_FROM = 5_000L
    const val BLOOM_SPREAD = 900L
    const val BLOOM_MILLIS = 420L
    const val FEATHERS_FROM = 5_150L

    /** Under the scene: the lot as the bird hovers, the price after the strike, and the coffee note at the end. */
    const val TITLE_FROM = 300L
    const val LABEL_FROM = 3_200L
    const val COFFEE_FROM = 6_300L
    const val END = 7_400L

    /** Beats per second while hovering and while climbing with the lot. */
    private const val HOVER_BEATS = 7.5f
    private const val CLIMB_BEATS = 4.2f

    fun progress(
        timeMillis: Long,
        from: Long,
        to: Long,
    ): Float = ((timeMillis - from).toFloat() / (to - from)).coerceIn(0f, 1f)

    fun progress(
        value: Float,
        from: Float,
        to: Float,
    ): Float = ((value - from) / (to - from)).coerceIn(0f, 1f)

    /** The bird at [timeMillis] in a scene of [size]. */
    fun bird(
        timeMillis: Long,
        size: Size,
    ): BirdPose {
        val t = timeMillis.coerceIn(0L, END)
        val layout = SceneLayout(size)
        val unit = layout.unit
        return when {
            t < FOLD_FROM -> {
                val beat = wingbeat(t / 1000f * HOVER_BEATS, depth = 0.78f)
                BirdPose(
                    position = layout.hover,
                    unit = unit,
                    pitch = -26f,
                    wingAngle = beat.first,
                    wingSpan = beat.second,
                    tailFan = 1f,
                    // The body rides each beat; the head stays level and where it is.
                    bob = 1.3f * sin(2f * PI.toFloat() * t / 1000f * HOVER_BEATS),
                    headTurn = 26f,
                )
            }

            t < STOOP_FROM -> {
                val p = easeOut(progress(t, FOLD_FROM, STOOP_FROM))
                BirdPose(
                    position = layout.hover - Offset(0f, 7f * unit * p),
                    unit = unit,
                    pitch = lerp(-26f, 48f, p),
                    wingAngle = lerp(-120f, -178f, p),
                    wingSpan = lerp(0.9f, 0.8f, p),
                    wingChord = lerp(1f, 0.72f, p),
                    tailFan = 1f - p,
                    headTurn = lerp(26f, 0f, p),
                    fold = p,
                )
            }

            t < STRIKE -> {
                val p = progress(t, STOOP_FROM, STRIKE)
                val flare = easeInOut(progress(t, FLARE_FROM, STRIKE))
                val travel = easeIn(p)
                val start = layout.hover - Offset(0f, 7f * unit)
                val end = layout.strikeOrigin
                val control = Offset(lerp(start.x, end.x, 0.15f), lerp(start.y, end.y, 0.62f))
                BirdPose(
                    position = quadratic(start, control, end, travel),
                    unit = unit,
                    pitch = lerp(lerp(48f, 72f, p), -12f, flare),
                    wingAngle = lerp(-178f, -98f, flare),
                    wingSpan = lerp(0.8f, 1f, flare),
                    wingChord = lerp(0.72f, 1f, flare),
                    tailFan = flare,
                    legs = flare,
                    legAngle = -28f,
                    fold = 1f - flare,
                )
            }

            t < CLIMB_FROM -> {
                val beat = wingbeat((t - STRIKE) / 1000f * CLIMB_BEATS)
                BirdPose(
                    position = layout.strikeOrigin,
                    unit = unit,
                    pitch = lerp(-12f, -30f, progress(t, STRIKE, CLIMB_FROM)),
                    wingAngle = beat.first,
                    wingSpan = beat.second,
                    tailFan = 1f,
                    legs = 1f,
                    legAngle = lerp(-28f, 12f, progress(t, STRIKE, CLIMB_FROM)),
                )
            }

            t < GLIDE_FROM -> {
                val p = progress(t, CLIMB_FROM, GLIDE_FROM)
                val beat = wingbeat((t - STRIKE) / 1000f * CLIMB_BEATS)
                val turn = easeInOut(progress(t, TURN_FROM, TURN_TO))
                BirdPose(
                    position = layout.climb(easeInOut(p)),
                    unit = unit,
                    facing = lerp(1f, -1f, turn),
                    pitch = lerp(-30f, -6f, p),
                    wingAngle = beat.first,
                    wingSpan = beat.second,
                    tailFan = lerp(1f, 0.4f, p),
                    legs = lerp(1f, 0.7f, p),
                    legAngle = 14f,
                    headTurn = lerp(21f, 4f, p),
                )
            }

            t < LAND_FROM -> {
                val p = progress(t, GLIDE_FROM, LAND_FROM)
                BirdPose(
                    position = lerp(layout.glideStart, layout.flare, easeInOut(p)),
                    unit = unit,
                    facing = -1f,
                    pitch = lerp(-6f, 8f, p),
                    wingAngle = -118f,
                    wingSpan = 0.62f,
                    tailFan = 0.4f,
                    legs = 0.7f,
                    legAngle = 14f,
                    headTurn = lerp(4f, -6f, p),
                )
            }

            t < PERCH -> {
                val p = easeOut(progress(t, LAND_FROM, PERCH))
                val beat = wingbeat((t - LAND_FROM) / 1000f * 5f, depth = 0.55f)
                BirdPose(
                    position = lerp(layout.flare, layout.perchOrigin(-1f), p),
                    unit = unit,
                    facing = -1f,
                    pitch = lerp(8f, -76f, p),
                    wingAngle = beat.first,
                    wingSpan = beat.second,
                    tailFan = lerp(0.4f, 1f, p),
                    legs = 1f,
                    legAngle = lerp(-20f, 76f, p),
                    headTurn = lerp(-6f, 76f, p),
                )
            }

            else -> {
                val turnRound = easeInOut(progress(t, PERCH, TURN_ROUND_TO))
                val facing = lerp(-1f, 1f, turnRound)
                // Wings thrown up over the prize with a little overshoot, held with a shiver, then folded away.
                val mantle = easeOutBack(progress(t, MANTLE_FROM, MANTLE_PEAK))
                val release = easeInOut(progress(t, MANTLE_HOLD_TO, SETTLED))
                val spread = mantle * (1f - release)
                val held = progress(t, MANTLE_PEAK, MANTLE_HOLD_TO)
                val shiver =
                    if (t in
                        MANTLE_PEAK..MANTLE_HOLD_TO
                    ) {
                        5f * sin(2f * PI.toFloat() * 7f * held * 0.6f) * (1f - held)
                    } else {
                        0f
                    }
                val look = progress(t, LOOK_FROM, LOOK_TO)
                val pitch = -76f - 5f * spread
                val near = lerp(lerp(-100f, -52f, mantle) + shiver, -182f, release)
                val far = lerp(lerp(-112f, 34f, mantle) - shiver, -184f, release)
                BirdPose(
                    // The feet stay on the post while the bird turns round and spreads its wings on them.
                    position = layout.perchOrigin(facing, pitch),
                    unit = unit,
                    facing = facing,
                    pitch = pitch,
                    wingAngle = near,
                    farWingAngle = far,
                    wingSpan = lerp(lerp(0.8f, 1.15f, mantle.coerceAtMost(1f)), 0.74f, release),
                    wingChord = lerp(1f, 0.72f, release),
                    tailFan = 1f - release,
                    legs = 1f,
                    // Legs straight down whatever the body's lean.
                    legAngle = -pitch,
                    // Beak up to call, then level, then one curious bob of the head, as kestrels do.
                    headTurn = -pitch - 20f * spread + 16f * sin(PI.toFloat() * look),
                    fold = release,
                )
            }
        }
    }

    /** The Easter egg's call: how long one tap's head bob, wing flick and word last. */
    const val CHIRP_MILLIS = 760L

    /**
     * The perched kestrel calling when tapped after the celebration ([progress] 0 to 1): beak up, a flick of the
     * wings off its back, a fan of the tail and a small hop, then back exactly as it was.
     */
    fun chirping(
        perched: BirdPose,
        progress: Float,
    ): BirdPose {
        val lift = sin(PI.toFloat() * progress)
        // The wings flick early and are back on the back well before the head comes down.
        val flick = sin(PI.toFloat() * (progress * 1.6f).coerceAtMost(1f))
        return perched.copy(
            headTurn = perched.headTurn - 24f * lift,
            wingAngle = perched.wingAngle + 24f * flick,
            fold = perched.fold - 0.55f * flick,
            tailFan = perched.tailFan + 0.6f * lift,
            bob = perched.bob - 1.6f * lift,
        )
    }

    /** Where the perched kestrel can be tapped: a generous box round its body, head and tail. */
    fun perchedBird(size: Size): androidx.compose.ui.geometry.Rect {
        val pose = bird(END, size)
        val centre = pose.toScene(Offset(2f, -6f))
        val unit = SceneLayout(size).unit
        return androidx.compose.ui.geometry.Rect(
            centre - Offset(34f * unit, 46f * unit),
            centre + Offset(34f * unit, 56f * unit),
        )
    }

    /** How far each call ("kee", "kee", "kee!") has risen from the beak (0 to 1), or null when it is not showing. */
    fun calls(timeMillis: Long): List<Float?> =
        CALLS.map { at ->
            val p = progress(timeMillis, at, at + CALL_MILLIS)
            p.takeIf { timeMillis >= at && it < 1f }
        }

    /** The tag: where its string is tied and how far it swings from hanging straight down (degrees). */
    fun tag(
        timeMillis: Long,
        size: Size,
    ): TagState {
        val t = timeMillis.coerceIn(0L, END)
        val layout = SceneLayout(size)
        if (t < STRIKE) return TagState(layout.tagRest, swing = 0f, lying = true)
        val pose = bird(t, size)
        val before = bird((t - 40L).coerceAtLeast(STRIKE), size)
        val velocityX = (pose.talons.x - before.talons.x) / layout.unit / 0.04f
        val since = (t - STRIKE).toFloat()
        val drag = (-velocityX * 0.22f).coerceIn(-34f, 34f)
        val settle = if (t >= PERCH) 1f - progress(t, PERCH, PERCH + 480L) else 1f
        val wobble = 20f * exp(-since / 650f) * sin(2f * PI.toFloat() * since / 520f)
        return TagState(pose.talons, swing = (drag + wobble) * settle, lying = false)
    }
}

internal enum class FlowerKind { Poppy, Cornflower, Daisy, Buttercup }

/** A wildflower of the bloom: it opens at [bloomAt] with a little overshoot and stays open. */
internal data class Flower(
    val at: Offset,
    val kind: FlowerKind,
    val radius: Float,
    val bloomAt: Long,
)

/** A feather from the mantle: it puffs out, drifts down swaying from [start] and rests at [rest]. */
internal data class Feather(
    val start: Offset,
    val rest: Offset,
    val from: Long,
    val fallMillis: Long,
    val phase: Float,
    val blue: Boolean,
) {
    /** Where it is at [timeMillis] and how far it has turned (degrees); null before it is shaken loose. */
    fun at(timeMillis: Long): Pair<Offset, Float>? {
        if (timeMillis < from) return null
        val p = CelebrationTimeline.progress(timeMillis, from, from + fallMillis)
        val fall = easeInOut(p)
        val sway = sin(2f * PI.toFloat() * 1.4f * p + phase) * (1f - p) * (rest.y - start.y) * 0.08f
        val position = Offset(lerp(start.x, rest.x, easeOut(p)) + sway, lerp(start.y, rest.y, fall))
        val turn = 50f * sin(2f * PI.toFloat() * 1.1f * p + phase) * (1f - p) + 70f + phase * 9f
        return position to turn
    }
}

/** The lot tag, tied at [anchor] (scene pixels), swinging [swing] degrees, or [lying] in the grass. */
internal data class TagState(
    val anchor: Offset,
    val swing: Float,
    val lying: Boolean,
)

/** Where things are in a scene of [size] (pixels): the bird is about 30 % of the width from tail to beak. */
internal class SceneLayout(
    val size: Size,
) {
    val unit: Float = size.width * 0.3f / 98f
    val hover = Offset(size.width * 0.46f, size.height * 0.29f)
    val tagRest = Offset(size.width * 0.67f, size.height * 0.855f)
    val postTop = Offset(size.width * 0.2f, size.height * 0.6f)
    val postWidth = size.width * 0.05f

    /** The body origin that puts the talons on the tag at the strike (pitch -12, legs forward). */
    val strikeOrigin: Offset =
        BirdPose(Offset.Zero, unit, pitch = -12f, legs = 1f, legAngle = -28f).let { pose ->
            tagRest - pose.talons + Offset(0f, -1.5f * unit)
        }

    /** The body origin of the bird standing on the post (legs straight down), facing [facing], leaning [pitch]. */
    fun perchOrigin(
        facing: Float,
        pitch: Float = -76f,
    ): Offset =
        BirdPose(Offset.Zero, unit, facing = facing, pitch = pitch, legs = 1f, legAngle = -pitch).let { pose ->
            postTop - pose.talons
        }

    /** The sun, which the rays break from. */
    val sunCentre = Offset(size.width * 0.76f, size.height * 0.62f)
    val sunRadius = size.width * 0.12f

    /** Thirty wildflowers across the meadow, each blooming as the wave from the post reaches it. */
    val flowers: List<Flower> =
        (0 until FLOWERS).map { index ->
            val x = size.width * ((index * 0.6180339f + 0.04f) % 1f)
            // In the meadow above the page's edge, which covers the scene's last few per cent.
            val y = size.height * (0.855f + 0.075f * ((index * 0.4142135f) % 1f))
            val reach = kotlin.math.abs(x - postTop.x) / size.width
            Flower(
                at = Offset(x, y),
                kind = FlowerKind.entries[index % FlowerKind.entries.size],
                radius = unit * (3.6f + 2.2f * ((index * 0.7071068f) % 1f)),
                bloomAt = CelebrationTimeline.BLOOM_FROM + (reach * CelebrationTimeline.BLOOM_SPREAD).toLong(),
            )
        }

    /** Nine feathers shaken loose by the mantle: where they start, where they come to rest in the grass. */
    val feathers: List<Feather> =
        (0 until FEATHERS).map { index ->
            val start = postTop + Offset((index - 4) * 3.2f * unit, (-34f + (index % 3) * 6f) * unit)
            val rest =
                Offset(
                    postTop.x + (index - 4) * 9f * unit + ((index * 0.618f) % 1f) * 6f * unit,
                    size.height * (0.9f + 0.07f * ((index * 0.4142f) % 1f)),
                )
            Feather(
                start = start,
                rest = rest,
                from = CelebrationTimeline.FEATHERS_FROM + index * 70L,
                fallMillis = 1_300L + (index % 4) * 120L,
                phase = index * 1.7f,
                blue = index % 3 == 2,
            )
        }

    private companion object {
        const val FLOWERS = 30
        const val FEATHERS = 9
    }

    val glideStart = Offset(size.width * 0.5f, size.height * 0.33f)
    val flare = Offset(size.width * 0.3f, size.height * 0.44f)

    /** Up and to the right with the lot, over the top, and back towards the post. */
    fun climb(p: Float): Offset {
        val c1 = Offset(size.width * 0.9f, size.height * 0.62f)
        val c2 = Offset(size.width * 0.74f, size.height * 0.16f)
        return cubic(strikeOrigin, c1, c2, glideStart, p)
    }
}

/** The scene's colours: a clear morning in the light theme, dusk in the dark theme. */
internal data class ScenePalette(
    val skyTop: Color,
    val skyMiddle: Color,
    val skyLow: Color,
    val sun: Color,
    val sunGlow: Color,
    val cloud: Color,
    val farHills: Color,
    val trees: Color,
    val midHills: Color,
    val meadow: Color,
    val meadowShade: Color,
    val grass: Color,
    val seeds: Color,
    val post: Color,
    val postSide: Color,
    val wire: Color,
    val tag: Color,
    val tagEdge: Color,
    val tagText: Color,
    val string: Color,
    val wind: Color,
    val call: Color,
) {
    companion object {
        val Morning =
            ScenePalette(
                skyTop = Color(0xFFBCCEDA),
                skyMiddle = Color(0xFFE8DECE),
                skyLow = Color(0xFFF4D1A6),
                sun = Color(0xFFFFF2D8),
                sunGlow = Color(0xFFFAD7A4),
                cloud = Color(0x99FFFFFF),
                farHills = Color(0xFFD5C5AA),
                trees = Color(0xFF9E9B73),
                midHills = Color(0xFFB7AB88),
                meadow = Color(0xFF8B9465),
                meadowShade = Color(0xFF6F7B50),
                grass = Color(0xFF5E6A41),
                seeds = Color(0xFFF6EBD0),
                post = Color(0xFF7C5E44),
                postSide = Color(0xFF5D4533),
                wire = Color(0x995D4533),
                tag = Color(0xFFEDD5A3),
                tagEdge = Color(0xFFC6A263),
                tagText = Color(0xFF5A4320),
                string = Color(0xFF7A6248),
                wind = Color(0xA6FFFFFF),
                call = Color(0xFF3A2D20),
            )

        val Dusk =
            ScenePalette(
                skyTop = Color(0xFF151E30),
                skyMiddle = Color(0xFF2E2D44),
                skyLow = Color(0xFF825447),
                sun = Color(0xFFF3A774),
                sunGlow = Color(0xFFC9714E),
                cloud = Color(0x33D9C4CF),
                farHills = Color(0xFF473A3C),
                trees = Color(0xFF2D2829),
                midHills = Color(0xFF332B2C),
                meadow = Color(0xFF282A22),
                meadowShade = Color(0xFF1E201A),
                grass = Color(0xFF33382A),
                seeds = Color(0xFFD9CBA9),
                post = Color(0xFF4E3B2E),
                postSide = Color(0xFF3A2C22),
                wire = Color(0x99231B15),
                tag = Color(0xFFDCC08A),
                tagEdge = Color(0xFFA98B52),
                tagText = Color(0xFF3E2E16),
                string = Color(0xFFA48D6E),
                wind = Color(0x40FFFFFF),
                call = Color(0xFFF6E7CF),
            )
    }
}

/** The words drawn into the scene: the tag's and the kestrel's three calls. */
internal class SceneWords(
    val tag: TextLayoutResult,
    val calls: List<TextLayoutResult>,
    val chirps: List<TextLayoutResult> = emptyList(),
)

/**
 * Draws the whole scene at [timeMillis]: sky, sun and its rays, clouds, hills, fence, the tag, the bird, the grass and
 * its flowers in front, the feathers and the calls.
 */
internal fun DrawScope.drawCelebrationScene(
    timeMillis: Long,
    palette: ScenePalette,
    words: SceneWords,
    chirp: ChirpFrame? = null,
) {
    val tagText = words.tag
    val t = timeMillis.coerceIn(0L, CelebrationTimeline.END)
    val layout = SceneLayout(size)
    val rise = (1f - easeOut(CelebrationTimeline.progress(t, 0L, 700L))) * 18f * layout.unit
    drawRect(
        Brush.verticalGradient(
            0f to palette.skyTop,
            0.48f to palette.skyMiddle,
            0.72f to palette.skyLow,
            startY = 0f,
            endY = size.height,
        ),
    )
    drawSunburst(layout, palette, t)
    drawSun(layout, palette, rise)
    drawClouds(layout, palette, t)
    drawWind(layout, palette, t)
    translate(0f, rise * 0.5f) { drawFarHills(layout, palette) }
    translate(0f, rise) {
        drawMidHills(layout, palette)
        drawFence(layout, palette)
        drawMeadow(layout, palette)
    }
    val tag = CelebrationTimeline.tag(t, size)
    if (tag.lying) drawTag(tag, layout, palette, tagText)
    drawSpeedLines(layout, t)
    val perched = CelebrationTimeline.bird(t, size)
    val calling = chirp?.takeIf { t >= CelebrationTimeline.END && !it.still }
    drawKestrel(calling?.let { CelebrationTimeline.chirping(perched, it.progress) } ?: perched)
    if (!tag.lying) drawTag(tag, layout, palette, tagText)
    translate(0f, rise) { drawGrass(layout, palette, t) }
    drawFlowers(layout, palette, t)
    drawStrikePuff(layout, palette, t)
    drawFeathers(layout, t)
    drawCalls(t, words.calls, palette)
    if (t >= CelebrationTimeline.END) chirp?.let { drawChirp(it, words, palette) }
}

/** One tap's call: which word, how far it has gone (0 to 1), and [still] for reduced motion (no bob, a still word). */
data class ChirpFrame(
    val progress: Float,
    val word: Int,
    val still: Boolean = false,
)

/** The tapped kestrel's word, popping out by the beak and rising as it fades, like the celebration's calls. */
private fun DrawScope.drawChirp(
    chirp: ChirpFrame,
    words: SceneWords,
    palette: ScenePalette,
) {
    val text = words.chirps.getOrNull(chirp.word % words.chirps.size.coerceAtLeast(1)) ?: return
    val layout = SceneLayout(size)
    val p = if (chirp.still) 0.3f else chirp.progress
    val bird = CelebrationTimeline.bird(CelebrationTimeline.END, size)
    val beak = bird.toScene(KestrelShape.Beak.getBounds().topRight)
    val pop = if (chirp.still) 1f else easeOutBack(CelebrationTimeline.progress(p, 0f, 0.3f))
    val alpha = if (chirp.still || p < 0.65f) 1f else 1f - (p - 0.65f) / 0.35f
    val centre = beak + Offset(18f * layout.unit, -(12f + 18f * easeOut(p)) * layout.unit)
    val topLeft = centre - Offset(text.size.width / 2f, text.size.height / 2f)
    withTransformScale(0.6f + 0.4f * pop, centre) {
        drawText(text, color = palette.call.copy(alpha = alpha), topLeft = topLeft)
    }
}

/** Rays breaking from the sun as the kestrel mantles: they grow, turn a few degrees once, and soften but stay. */
private fun DrawScope.drawSunburst(
    layout: SceneLayout,
    palette: ScenePalette,
    t: Long,
) {
    val grow =
        easeOut(
            CelebrationTimeline.progress(
                t,
                CelebrationTimeline.SUNBURST_FROM,
                CelebrationTimeline.SUNBURST_FROM + 750L,
            ),
        )
    if (grow <= 0f) return
    val soften =
        1f - 0.5f * CelebrationTimeline.progress(t, CelebrationTimeline.SUNBURST_FROM + 750L, CelebrationTimeline.END)
    val turn = 9f * easeOut(CelebrationTimeline.progress(t, CelebrationTimeline.SUNBURST_FROM, CelebrationTimeline.END))
    val centre = layout.sunCentre
    val radius = layout.sunRadius
    val reach = radius * (1.2f + 2.6f * grow)
    val brush =
        Brush.radialGradient(
            0f to palette.sunGlow.copy(alpha = 0.85f * soften),
            1f to palette.sunGlow.copy(alpha = 0f),
            center = centre,
            radius = reach,
        )
    for (index in 0 until RAYS) {
        val middle = (index * 360f / RAYS + turn) * PI.toFloat() / 180f
        val length = if (index % 2 == 0) reach else reach * 0.74f
        val half = (if (index % 2 == 0) 4.2f else 3f) * PI.toFloat() / 180f
        val inner = radius * 1.05f
        val path =
            Path().apply {
                moveTo(centre.x + cos(middle - half * 0.4f) * inner, centre.y + sin(middle - half * 0.4f) * inner)
                lineTo(centre.x + cos(middle - half) * length, centre.y + sin(middle - half) * length)
                lineTo(centre.x + cos(middle + half) * length, centre.y + sin(middle + half) * length)
                lineTo(centre.x + cos(middle + half * 0.4f) * inner, centre.y + sin(middle + half * 0.4f) * inner)
                close()
            }
        drawPath(path, brush)
    }
}

private const val RAYS = 18

/** The meadow blooms in a wave from the post: each flower opens with a little overshoot and stays open. */
internal fun DrawScope.drawFlowers(
    layout: SceneLayout,
    palette: ScenePalette,
    t: Long,
    flowers: List<Flower> = layout.flowers,
) {
    for (flower in flowers) {
        val open =
            easeOutBack(
                CelebrationTimeline.progress(
                    t,
                    flower.bloomAt,
                    flower.bloomAt + CelebrationTimeline.BLOOM_MILLIS,
                ),
            )
        if (open <= 0f) continue
        val r = flower.radius * open
        val centre = flower.at - Offset(0f, 6f * layout.unit * open)
        drawLine(palette.grass, flower.at, centre, strokeWidth = 0.9f * layout.unit, cap = StrokeCap.Round)
        val (petal, heart) = flowerColours(flower.kind, palette)
        val petals = if (flower.kind == FlowerKind.Daisy) 8 else 5
        for (index in 0 until petals) {
            val angle = (index * 360f / petals + flower.at.x % 40f) * PI.toFloat() / 180f
            drawCircle(petal, radius = r * 0.52f, center = centre + Offset(cos(angle), sin(angle)) * (r * 0.55f))
        }
        drawCircle(heart, radius = r * 0.38f, center = centre)
    }
}

private fun flowerColours(
    kind: FlowerKind,
    palette: ScenePalette,
): Pair<Color, Color> {
    val dusk = palette == ScenePalette.Dusk
    val (petal, heart) =
        when (kind) {
            FlowerKind.Poppy -> Color(0xFFD9452F) to Color(0xFF2B2220)
            FlowerKind.Cornflower -> Color(0xFF5677CC) to Color(0xFF2C3A72)
            FlowerKind.Daisy -> Color(0xFFFFFCF4) to Color(0xFFF2C230)
            FlowerKind.Buttercup -> Color(0xFFF5C94A) to Color(0xFFD9961C)
        }
    return if (dusk) petal.darken(0.78f) to heart.darken(0.78f) else petal to heart
}

/** The feathers the mantle shakes loose, drifting down and coming to rest in the grass. */
private fun DrawScope.drawFeathers(
    layout: SceneLayout,
    t: Long,
) {
    for (feather in layout.feathers) drawFeather(feather, t, layout.unit)
}

/** One feather as it falls at [t], or nothing before it comes loose. */
internal fun DrawScope.drawFeather(
    feather: Feather,
    t: Long,
    u: Float,
) {
    val (position, turn) = feather.at(t) ?: return
    val body = if (feather.blue) Plumage.tail else Plumage.back
    rotate(turn, position) {
        val length = 6.5f * u
        val width = 2.6f * u
        val path =
            Path().apply {
                moveTo(position.x, position.y - length)
                quadraticTo(position.x + width, position.y - length * 0.1f, position.x, position.y + length)
                quadraticTo(position.x - width, position.y - length * 0.1f, position.x, position.y - length)
                close()
            }
        drawPath(path, body)
        // A dark bar across, as on a kestrel's wing and tail feathers, and the shaft.
        val bar = Offset(1.7f * u, 0f)
        drawLine(
            Plumage.spots,
            position + Offset(0f, 2.6f * u) - bar,
            position + Offset(0f, 2.6f * u) + bar,
            strokeWidth =
                1.1f * u,
        )
        drawLine(
            body.darken(0.65f),
            position - Offset(0f, length),
            position + Offset(0f, length),
            strokeWidth =
                0.4f * u,
        )
    }
}

/** "kee", "kee", "kee!": each pops out by the beak, rises and fades. */
private fun DrawScope.drawCalls(
    t: Long,
    calls: List<TextLayoutResult>,
    palette: ScenePalette,
) {
    val progress = CelebrationTimeline.calls(t)
    if (progress.all { it == null }) return
    val layout = SceneLayout(size)
    val bird = CelebrationTimeline.bird(t, size)
    val beak = bird.toScene(KestrelShape.Beak.getBounds().topRight)
    for ((index, p) in progress.withIndex()) {
        if (p == null) continue
        val text = calls.getOrNull(index) ?: continue
        val pop = easeOutBack(CelebrationTimeline.progress(p, 0f, 0.35f))
        val alpha = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f
        // An arc up and away from the beak, each call higher and further than the last.
        val lift = (10f + index * 13f + 14f * easeOut(p)) * layout.unit
        val sideways = (16f + index * 19f + 4f * p) * layout.unit
        val topLeft = beak + Offset(sideways, -lift) - Offset(text.size.width / 2f, text.size.height / 2f)
        val scale = 0.6f + 0.4f * pop
        withTransformScale(scale, topLeft + Offset(text.size.width / 2f, text.size.height / 2f)) {
            drawText(text, color = palette.call.copy(alpha = alpha), topLeft = topLeft)
        }
    }
}

private inline fun DrawScope.withTransformScale(
    scale: Float,
    pivot: Offset,
    block: DrawScope.() -> Unit,
) = scale(scale, scale, pivot, block)

internal fun DrawScope.drawSun(
    layout: SceneLayout,
    palette: ScenePalette,
    rise: Float,
) {
    val centre = layout.sunCentre + Offset(0f, rise * 0.3f)
    val radius = layout.sunRadius
    drawCircle(
        Brush.radialGradient(
            0f to palette.sunGlow.copy(alpha = 0.55f),
            1f to palette.sunGlow.copy(alpha = 0f),
            center = centre,
            radius = radius * 2.6f,
        ),
        radius = radius * 2.6f,
        center = centre,
    )
    drawCircle(palette.sun, radius = radius, center = centre)
}

internal fun DrawScope.drawClouds(
    layout: SceneLayout,
    palette: ScenePalette,
    t: Long,
) {
    val drift = t / 1000f * 6f * layout.unit
    cloud(Offset(size.width * 0.2f - drift, size.height * 0.16f), size.width * 0.34f, palette.cloud)
    cloud(Offset(size.width * 0.86f - drift * 0.7f, size.height * 0.36f), size.width * 0.26f, palette.cloud)
}

internal fun DrawScope.cloud(
    centre: Offset,
    width: Float,
    colour: Color,
) {
    val height = width * 0.16f
    drawOval(colour, topLeft = centre - Offset(width / 2f, height / 2f), size = Size(width, height))
    drawOval(
        colour,
        topLeft = centre - Offset(width * 0.22f, height * 1.05f),
        size = Size(width * 0.42f, height * 1.3f),
    )
}

internal fun DrawScope.drawWind(
    layout: SceneLayout,
    palette: ScenePalette,
    t: Long,
) {
    if (t >= CelebrationTimeline.STOOP_FROM) return
    val fade = 1f - CelebrationTimeline.progress(t, CelebrationTimeline.FOLD_FROM, CelebrationTimeline.STOOP_FROM)
    for (index in 0 until 3) {
        val period = 1_700f
        val local = ((t + index * 560L) % period.toLong()) / period
        val x = size.width * (1.1f - 1.35f * local)
        // Below the bird, so a gust never crosses its face.
        val y = layout.hover.y + listOf(34f, 48f, 62f)[index] * layout.unit
        val alpha = sin(PI.toFloat() * local) * fade
        val length = 34f * layout.unit
        val path =
            Path().apply {
                moveTo(x, y)
                cubicTo(x + length * 0.3f, y - 2f * layout.unit, x + length * 0.7f, y + 2f * layout.unit, x + length, y)
            }
        drawPath(
            path,
            palette.wind.copy(alpha = palette.wind.alpha * alpha),
            style = Stroke(0.9f * layout.unit, cap = StrokeCap.Round),
        )
    }
}

internal fun DrawScope.drawFarHills(
    layout: SceneLayout,
    palette: ScenePalette,
) {
    val w = size.width
    val h = size.height
    val path =
        Path().apply {
            moveTo(0f, h * 0.66f)
            cubicTo(w * 0.18f, h * 0.6f, w * 0.34f, h * 0.63f, w * 0.5f, h * 0.65f)
            cubicTo(w * 0.68f, h * 0.67f, w * 0.82f, h * 0.61f, w, h * 0.63f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
    drawPath(path, palette.farHills)
}

internal fun DrawScope.drawMidHills(
    layout: SceneLayout,
    palette: ScenePalette,
) {
    val w = size.width
    val h = size.height
    for ((x, r) in listOf(0.56f to 0.035f, 0.61f to 0.027f, 0.9f to 0.032f)) {
        val centre = Offset(w * x, h * 0.69f)
        drawCircle(palette.trees, radius = w * r, center = centre)
        drawLine(palette.trees, centre, centre + Offset(0f, w * r * 1.8f), strokeWidth = layout.unit * 1.6f)
    }
    val path =
        Path().apply {
            moveTo(0f, h * 0.74f)
            cubicTo(w * 0.22f, h * 0.7f, w * 0.42f, h * 0.73f, w * 0.6f, h * 0.72f)
            cubicTo(w * 0.78f, h * 0.71f, w * 0.9f, h * 0.73f, w, h * 0.72f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
    drawPath(path, palette.midHills)
}

internal fun DrawScope.drawFence(
    layout: SceneLayout,
    palette: ScenePalette,
) {
    val h = size.height
    val top = layout.postTop
    val width = layout.postWidth
    // Two sagging wires to a second post at the right edge.
    val farPost = Offset(size.width * 0.97f, h * 0.66f)
    for (drop in listOf(0.06f, 0.15f)) {
        val start = Offset(top.x, top.y + h * drop)
        val end = Offset(farPost.x, farPost.y + h * drop * 0.7f)
        val path =
            Path().apply {
                moveTo(start.x, start.y)
                quadraticTo((start.x + end.x) / 2f, (start.y + end.y) / 2f + h * 0.03f, end.x, end.y)
            }
        drawPath(path, palette.wire, style = Stroke(layout.unit * 0.5f))
    }
    drawRect(palette.post, topLeft = Offset(farPost.x - width * 0.35f, farPost.y), size = Size(width * 0.7f, h))
    // The near post, with a shaded side and a rounded top.
    drawRect(palette.post, topLeft = Offset(top.x - width / 2f, top.y), size = Size(width, h - top.y))
    drawRect(palette.postSide, topLeft = Offset(top.x + width * 0.18f, top.y), size = Size(width * 0.32f, h - top.y))
    drawOval(
        palette.postSide,
        topLeft = Offset(top.x - width / 2f, top.y - width * 0.12f),
        size =
            Size(
                width,
                width * 0.24f,
            ),
    )
    for (index in 1..3) {
        val y = top.y + index * width * 1.9f
        drawLine(
            palette.postSide,
            Offset(top.x - width * 0.38f, y),
            Offset(top.x - width * 0.05f, y + width * 0.5f),
            strokeWidth =
                layout.unit * 0.45f,
        )
    }
}

internal fun DrawScope.drawMeadow(
    layout: SceneLayout,
    palette: ScenePalette,
) {
    val w = size.width
    val h = size.height
    val path =
        Path().apply {
            moveTo(0f, h * 0.83f)
            cubicTo(w * 0.25f, h * 0.8f, w * 0.5f, h * 0.84f, w * 0.72f, h * 0.82f)
            cubicTo(w * 0.86f, h * 0.81f, w * 0.94f, h * 0.83f, w, h * 0.82f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
    drawPath(path, Brush.verticalGradient(0f to palette.meadow, 1f to palette.meadowShade, startY = h * 0.8f, endY = h))
}

internal fun DrawScope.drawGrass(
    layout: SceneLayout,
    palette: ScenePalette,
    t: Long,
) {
    val h = size.height
    val sway = sin(2f * PI.toFloat() * t / 1000f * 0.55f)
    for (index in 0 until 34) {
        val x = size.width * ((index * 0.6180339f) % 1f)
        val base = h * (0.9f + 0.1f * ((index * 0.381966f) % 1f))
        val length = layout.unit * (7f + 6f * ((index * 0.7548777f) % 1f))
        val lean = (sway * 1.6f + (index % 5 - 2) * 0.9f) * layout.unit
        drawLine(
            palette.grass,
            Offset(x, base),
            Offset(x + lean, base - length),
            strokeWidth = layout.unit * 0.9f,
            cap = StrokeCap.Round,
        )
    }
}

internal fun DrawScope.drawTag(
    tag: TagState,
    layout: SceneLayout,
    palette: ScenePalette,
    text: TextLayoutResult,
) {
    val u = layout.unit
    val width = 22f * u
    val height = 12.5f * u
    if (tag.lying) {
        // In the grass, tilted, its string in a loose loop.
        rotate(-14f, tag.anchor) {
            val topLeft = tag.anchor - Offset(width / 2f, height / 2f)
            tagShape(topLeft, width, height, palette, text, u)
            val hole = topLeft + Offset(width * 0.14f, height / 2f)
            val loop =
                Path().apply {
                    moveTo(hole.x, hole.y)
                    cubicTo(
                        hole.x - 8f * u,
                        hole.y - 6f * u,
                        hole.x - 9f * u,
                        hole.y + 5f * u,
                        hole.x - 3f * u,
                        hole.y + 3f * u,
                    )
                }
            drawPath(loop, palette.string, style = Stroke(0.55f * u, cap = StrokeCap.Round))
        }
        return
    }
    val string = 10f * u
    rotate(tag.swing, tag.anchor) {
        val hole = tag.anchor + Offset(0f, string)
        drawLine(palette.string, tag.anchor, hole, strokeWidth = 0.55f * u, cap = StrokeCap.Round)
        // Hanging by its hole, so the tag stands on its narrow end.
        rotate(90f, hole) {
            tagShape(hole - Offset(width * 0.14f, height / 2f), width, height, palette, text, u)
        }
    }
}

/** A manila lot tag: clipped corners at the hole end, a reinforced hole and the word on it. */
private fun DrawScope.tagShape(
    topLeft: Offset,
    width: Float,
    height: Float,
    palette: ScenePalette,
    text: TextLayoutResult,
    u: Float,
) {
    val corner = height * 0.32f
    val path =
        Path().apply {
            moveTo(topLeft.x + corner, topLeft.y)
            lineTo(topLeft.x + width, topLeft.y)
            lineTo(topLeft.x + width, topLeft.y + height)
            lineTo(topLeft.x + corner, topLeft.y + height)
            lineTo(topLeft.x, topLeft.y + height - corner)
            lineTo(topLeft.x, topLeft.y + corner)
            close()
        }
    drawPath(path, palette.tag)
    drawPath(path, palette.tagEdge, style = Stroke(0.5f * u))
    val hole = topLeft + Offset(width * 0.14f, height / 2f)
    drawCircle(palette.tagEdge, radius = 2.1f * u, center = hole)
    drawCircle(palette.postSide, radius = 1.1f * u, center = hole)
    val textTopLeft =
        Offset(
            topLeft.x + width * 0.28f + (width * 0.68f - text.size.width) / 2f,
            topLeft.y + (height - text.size.height) / 2f,
        )
    drawText(text, color = palette.tagText, topLeft = textTopLeft)
}

internal fun DrawScope.drawSpeedLines(
    layout: SceneLayout,
    t: Long,
) {
    if (t < CelebrationTimeline.STOOP_FROM + 120L || t > CelebrationTimeline.STRIKE) return
    val now = CelebrationTimeline.bird(t, size).position
    val back = CelebrationTimeline.bird((t - 110L).coerceAtLeast(CelebrationTimeline.STOOP_FROM), size).position
    val direction = now - back
    val length = direction.getDistance().coerceAtLeast(1f)
    val normal = Offset(-direction.y / length, direction.x / length)
    val fade = 1f - CelebrationTimeline.progress(t, CelebrationTimeline.FLARE_FROM, CelebrationTimeline.STRIKE)
    for (index in -1..1) {
        val offset = normal * (index * 7f * layout.unit)
        drawLine(
            Color.White.copy(alpha = 0.4f * fade),
            back + offset - direction * 0.3f,
            now + offset - direction * 0.45f,
            strokeWidth = 0.8f * layout.unit,
            cap = StrokeCap.Round,
        )
    }
}

internal fun DrawScope.drawStrikePuff(
    layout: SceneLayout,
    palette: ScenePalette,
    t: Long,
) {
    val p = CelebrationTimeline.progress(t, CelebrationTimeline.STRIKE, CelebrationTimeline.STRIKE + 650L)
    if (p <= 0f || p >= 1f) return
    val origin = layout.tagRest
    val spread = easeOut(p) * 26f * layout.unit
    for (index in 0 until 9) {
        val angle = (-160f + index * 17.5f) * PI.toFloat() / 180f
        val point = origin + Offset(cos(angle) * spread, sin(angle) * spread * 0.8f + p * p * 10f * layout.unit)
        drawCircle(
            palette.seeds.copy(alpha = 1f - p),
            radius = (0.9f + (index % 3) * 0.3f) * layout.unit,
            center = point,
        )
    }
    drawCircle(
        palette.seeds.copy(alpha = 0.6f * (1f - p)),
        radius = 6f * layout.unit + spread,
        center = origin,
        style = Stroke(0.6f * layout.unit),
    )
}

internal fun easeOut(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

/** Overshoots a little and comes back: for things that open with joy (wings, flowers). */
internal fun easeOutBack(p: Float): Float {
    val c = 1.7f
    val q = p - 1f
    return 1f + (c + 1f) * q * q * q + c * q * q
}

internal fun easeIn(p: Float): Float = p * p * p

internal fun easeInOut(p: Float): Float =
    if (p <
        0.5f
    ) {
        4f * p * p * p
    } else {
        1f - (-2f * p + 2f).let { it * it * it } / 2f
    }

private fun quadratic(
    start: Offset,
    control: Offset,
    end: Offset,
    p: Float,
): Offset = lerp(lerp(start, control, p), lerp(control, end, p), p)

private fun cubic(
    start: Offset,
    c1: Offset,
    c2: Offset,
    end: Offset,
    p: Float,
): Offset {
    val a = lerp(start, c1, p)
    val b = lerp(c1, c2, p)
    val c = lerp(c2, end, p)
    return lerp(lerp(a, b, p), lerp(b, c, p), p)
}
