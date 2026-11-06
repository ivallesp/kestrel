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
 * folds and stoops onto the lot's tag in the grass, carries it up, turns and lands on a fence post, and looks round.
 * It plays once; nothing loops.
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
    const val SETTLED = 5_100L
    const val LOOK_FROM = 5_300L
    const val LOOK_TO = 5_900L

    /** Under the scene: the lot as the bird hovers, the price after the strike, and the coffee note at the end. */
    const val TITLE_FROM = 300L
    const val LABEL_FROM = 3_200L
    const val COFFEE_FROM = 4_800L
    const val END = 6_000L

    /** Beats per second while hovering and while climbing with the lot. */
    private const val HOVER_BEATS = 7.5f
    private const val CLIMB_BEATS = 4.2f

    fun progress(
        timeMillis: Long,
        from: Long,
        to: Long,
    ): Float = ((timeMillis - from).toFloat() / (to - from)).coerceIn(0f, 1f)

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
                val settle = easeOut(progress(t, PERCH, SETTLED))
                val turnRound = easeInOut(progress(t, PERCH, TURN_ROUND_TO))
                val look = progress(t, LOOK_FROM, LOOK_TO)
                val facing = lerp(-1f, 1f, turnRound)
                BirdPose(
                    // The feet stay on the post while the bird turns round on them.
                    position = layout.perchOrigin(facing),
                    unit = unit,
                    facing = facing,
                    pitch = -76f,
                    wingAngle = lerp(-100f, 178f, settle),
                    wingSpan = lerp(0.8f, 0.74f, settle),
                    wingChord = lerp(1f, 0.72f, settle),
                    tailFan = lerp(1f, 0f, settle),
                    legs = 1f,
                    legAngle = 76f,
                    // Level, then one curious bob of the head, as kestrels do, and back.
                    headTurn = 76f + 16f * sin(PI.toFloat() * look),
                    fold = settle,
                )
            }
        }
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
        val settle = if (t >= PERCH) 1f - progress(t, PERCH, SETTLED) else 1f
        val wobble = 20f * exp(-since / 650f) * sin(2f * PI.toFloat() * since / 520f)
        return TagState(pose.talons, swing = (drag + wobble) * settle, lying = false)
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

    /** The body origin of the bird standing on the post (upright, legs straight down), facing [facing]. */
    fun perchOrigin(facing: Float): Offset =
        BirdPose(Offset.Zero, unit, facing = facing, pitch = -76f, legs = 1f, legAngle = 76f).let { pose ->
            postTop - pose.talons
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
            )
    }
}

/** Draws the whole scene at [timeMillis]: sky, sun, clouds, hills, fence, the tag, the bird and the grass in front. */
internal fun DrawScope.drawCelebrationScene(
    timeMillis: Long,
    palette: ScenePalette,
    tagText: TextLayoutResult,
) {
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
    drawKestrel(CelebrationTimeline.bird(t, size))
    if (!tag.lying) drawTag(tag, layout, palette, tagText)
    translate(0f, rise) { drawGrass(layout, palette, t) }
    drawStrikePuff(layout, palette, t)
}

private fun DrawScope.drawSun(
    layout: SceneLayout,
    palette: ScenePalette,
    rise: Float,
) {
    val centre = Offset(size.width * 0.76f, size.height * 0.62f + rise * 0.3f)
    val radius = size.width * 0.12f
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

private fun DrawScope.drawClouds(
    layout: SceneLayout,
    palette: ScenePalette,
    t: Long,
) {
    val drift = t / 1000f * 6f * layout.unit
    cloud(Offset(size.width * 0.2f - drift, size.height * 0.16f), size.width * 0.34f, palette.cloud)
    cloud(Offset(size.width * 0.86f - drift * 0.7f, size.height * 0.36f), size.width * 0.26f, palette.cloud)
}

private fun DrawScope.cloud(
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

private fun DrawScope.drawWind(
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

private fun DrawScope.drawFarHills(
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

private fun DrawScope.drawMidHills(
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

private fun DrawScope.drawFence(
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

private fun DrawScope.drawMeadow(
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

private fun DrawScope.drawGrass(
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

private fun DrawScope.drawTag(
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

private fun DrawScope.drawSpeedLines(
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

private fun DrawScope.drawStrikePuff(
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
