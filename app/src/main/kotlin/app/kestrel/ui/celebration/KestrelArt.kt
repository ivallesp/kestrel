package app.kestrel.ui.celebration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The launcher icon's male kestrel (blue-grey head, rufous back, black-banded grey tail), drawn side-on and facing
 * right in bird units: about 98 units from the tail tip to the beak, with the body's origin between the shoulders and
 * the hips. The plumage is the same in both themes, because it is a drawing of a bird, not a state colour.
 */
internal object Plumage {
    val head = Color(0xFF5A6EA6)
    val crown = Color(0xFF4D5F93)
    val cheek = Color(0xFFFFF6EC)
    val malar = Color(0xFF252D4B)
    val beak = Color(0xFF2B3147)
    val amber = Color(0xFFEC9F36)
    val iris = Color(0xFF1B1F2E)
    val back = Color(0xFFC0582A)
    val backShade = Color(0xFFA34A23)
    val spots = Color(0xFF3A2A20)
    val underside = Color(0xFFF3E3CC)
    val streak = Color(0xFF9A6646)
    val primaries = Color(0xFF3B332D)
    val secondaries = Color(0xFF4E3B2F)
    val tail = Color(0xFF7F8DB5)
    val tailBand = Color(0xFF232838)
    val tailTip = Color(0xFFF1E8DA)
}

/**
 * One moment of the bird. [wingAngle] is the direction the near wing points in the body's frame (0 forward, -90 up,
 * 180 back); [wingSpan] shortens a wing that points at the viewer. [bob] moves everything but the head (bird units),
 * which is how a hovering kestrel holds its head perfectly still. [facing] is 1 to the right and -1 to the left;
 * values in between are the moment of turning.
 */
internal data class BirdPose(
    val position: Offset,
    val unit: Float,
    val facing: Float = 1f,
    val pitch: Float = 0f,
    val wingAngle: Float = -110f,
    val wingSpan: Float = 1f,
    val wingChord: Float = 1f,
    val tailFan: Float = 0f,
    val legs: Float = 0f,
    val legAngle: Float = 0f,
    val bob: Float = 0f,
    val headTurn: Float = 0f,
    val fold: Float = 0f,
    val farWingAngle: Float? = null,
) {
    /** Turning, the bird narrows to a sliver and comes back the other way, never to nothing. */
    val drawnFacing: Float get() = if (facing < 0f) minOf(facing, -MIN_FACING) else maxOf(facing, MIN_FACING)

    /** A point of the bird (bird units) in scene pixels. */
    fun toScene(local: Offset): Offset {
        val radians = pitch * PI.toFloat() / 180f
        val x = local.x * cos(radians) - local.y * sin(radians)
        val y = local.x * sin(radians) + local.y * cos(radians)
        return Offset(position.x + x * unit * drawnFacing, position.y + y * unit)
    }

    /** Where the talons are, for the lot tag the bird carries. */
    val talons: Offset get() = toScene(KestrelShape.talonsFor(this))
}

/** The bird's fixed points and paths, in bird units. */
internal object KestrelShape {
    val Shoulder = Offset(6f, -9.5f)

    /** Where a folded wing lies: along the flank, from just below the shoulder. */
    val FlankRoot = Offset(9f, -5f)
    val Hip = Offset(5f, 9f)
    val Neck = Offset(20f, -12f)
    val HeadCentre = Offset(24f, -16f)
    val Eye = Offset(28.3f, -17.9f)
    const val LEG_LENGTH = 11f

    fun talonsFor(pose: BirdPose): Offset {
        val length = LEG_LENGTH * pose.legs + 1.5f
        val radians = pose.legAngle * PI.toFloat() / 180f
        // The legs hang along the body's "down", turned towards the tail by legAngle.
        return Offset(Hip.x - length * sin(radians), Hip.y + bob(pose) + length * cos(radians))
    }

    private fun bob(pose: BirdPose): Float = pose.bob

    val Body: Path by lazy {
        Path().apply {
            moveTo(-23f, -3f)
            cubicTo(-12f, -10f, 6f, -14f, 18f, -12.5f)
            cubicTo(26f, -11f, 30.5f, -3f, 26f, 4f)
            cubicTo(20f, 11f, 4f, 13f, -6f, 10.5f)
            cubicTo(-14f, 8.5f, -20f, 6f, -23f, 3f)
            close()
        }
    }

    val Mantle: Path by lazy {
        Path().apply {
            moveTo(-23f, -3f)
            cubicTo(-12f, -10f, 6f, -14f, 18f, -12.5f)
            cubicTo(14f, -6f, 1f, -2f, -22f, 1f)
            close()
        }
    }

    val Malar: Path by lazy {
        Path().apply {
            moveTo(27.2f, -15.6f)
            quadraticTo(29.6f, -11f, 26.8f, -7.4f)
            quadraticTo(25.4f, -11.2f, 25.6f, -15.2f)
            close()
        }
    }

    val Beak: Path by lazy {
        Path().apply {
            moveTo(31.6f, -19.5f)
            quadraticTo(37.6f, -19.8f, 38.3f, -14.3f)
            quadraticTo(36.4f, -15.8f, 33.8f, -15.5f)
            lineTo(32f, -15.9f)
            close()
        }
    }

    val MantleSpots =
        listOf(
            Offset(-14f, -5.5f),
            Offset(-7f, -8f),
            Offset(0f, -9.6f),
            Offset(7f, -10.6f),
            Offset(-3f, -5f),
            Offset(4f, -6.5f),
            Offset(11f, -9f),
            Offset(-10f, -2.5f),
        )
    val BellyStreaks =
        listOf(
            Offset(19f, 3f),
            Offset(14f, 6.5f),
            Offset(8f, 8.5f),
            Offset(1f, 9.4f),
            Offset(22f, -1.5f),
            Offset(-6f, 8f),
        )
}

private const val MIN_FACING = 0.18f

/** Draws the kestrel at [pose]: far wing, tail, legs, body, head, then the near wing over the body. */
internal fun DrawScope.drawKestrel(pose: BirdPose) {
    withTransform({
        translate(pose.position.x, pose.position.y)
        scale(pose.drawnFacing, 1f, Offset.Zero)
        rotate(pose.pitch, Offset.Zero)
        scale(pose.unit, pose.unit, Offset.Zero)
    }) {
        translate(0f, pose.bob) {
            drawWing(pose, near = false)
            drawTail(pose)
            drawLegs(pose)
            drawBody()
        }
        drawHead(pose)
        translate(0f, pose.bob) { drawWing(pose, near = true) }
    }
}

private fun DrawScope.drawTail(pose: BirdPose) {
    val fan = pose.tailFan
    val tipX = lerp(-60f, -51f, fan)
    val half = lerp(4.4f, 12.5f, fan)
    val root = Offset(-21f, 0f)
    val path =
        Path().apply {
            moveTo(root.x, -5f)
            lineTo(tipX, -half)
            quadraticTo(tipX - 4f - 7f * fan, 0f, tipX, half)
            lineTo(root.x, 5f)
            close()
        }
    // Fanned, the tail also bends down, as a hovering kestrel's does.
    rotate(-16f * fan, root) {
        val start = tipX - 6f
        drawPath(
            path,
            Brush.horizontalGradient(
                0f to Plumage.tailTip,
                0.08f to Plumage.tailTip,
                0.08f to Plumage.tailBand,
                0.22f to Plumage.tailBand,
                0.22f to Plumage.tail,
                1f to Plumage.tail,
                startX = start,
                endX = root.x,
            ),
        )
        // The fanned feathers' shafts.
        if (fan > 0.05f) {
            for (index in -2..2) {
                drawLine(
                    Plumage.tailBand.copy(alpha = 0.22f * fan),
                    Offset(root.x - 1f, index * 1.6f),
                    Offset(tipX + 3f, index * half * 0.38f),
                    strokeWidth = 0.45f,
                )
            }
        }
    }
}

private fun DrawScope.drawLegs(pose: BirdPose) {
    if (pose.legs <= 0.02f) return
    val length = KestrelShape.LEG_LENGTH * pose.legs
    rotate(pose.legAngle, KestrelShape.Hip) {
        for (dx in listOf(-2.2f, 2.4f)) {
            val top = KestrelShape.Hip + Offset(dx, 0f)
            val foot = top + Offset(0f, length)
            drawLine(Plumage.amber, top, foot, strokeWidth = 2.1f, cap = StrokeCap.Round)
            drawLine(Plumage.amber, foot, foot + Offset(2.8f, 0.8f), strokeWidth = 1.3f, cap = StrokeCap.Round)
            drawLine(Plumage.amber, foot, foot + Offset(-1.9f, 1.1f), strokeWidth = 1.3f, cap = StrokeCap.Round)
            drawCircle(Plumage.iris, radius = 0.55f, center = foot + Offset(3.1f, 1f))
        }
        // The feathered thighs.
        drawOval(Plumage.underside, topLeft = KestrelShape.Hip + Offset(-5.5f, -3.5f), size = Size(11f, 6.5f))
    }
}

private fun DrawScope.drawBody() {
    drawPath(KestrelShape.Body, Plumage.underside)
    for (streak in KestrelShape.BellyStreaks) {
        drawOval(Plumage.streak.copy(alpha = 0.75f), topLeft = streak - Offset(0.7f, 1.3f), size = Size(1.4f, 2.6f))
    }
    drawPath(
        KestrelShape.Mantle,
        Brush.verticalGradient(0f to Plumage.back, 1f to Plumage.backShade, startY = -13f, endY = 1f),
    )
    for (spot in KestrelShape.MantleSpots) drawCircle(Plumage.spots, radius = 0.95f, center = spot)
}

private fun DrawScope.drawHead(pose: BirdPose) {
    rotate(pose.headTurn, KestrelShape.Neck) {
        drawCircle(Plumage.head, radius = 9.6f, center = KestrelShape.HeadCentre)
        drawArc(
            Plumage.crown,
            startAngle = 200f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = KestrelShape.HeadCentre - Offset(9.6f, 9.6f),
            size = Size(19.2f, 19.2f),
            style =
                androidx.compose.ui.graphics.drawscope
                    .Stroke(width = 2.2f),
        )
        drawOval(Plumage.cheek, topLeft = Offset(21f, -15.2f), size = Size(10.8f, 7.9f))
        drawPath(KestrelShape.Malar, Plumage.malar)
        drawCircle(Plumage.amber, radius = 2.55f, center = KestrelShape.Eye)
        drawCircle(Plumage.iris, radius = 1.95f, center = KestrelShape.Eye)
        drawCircle(Color.White, radius = 0.62f, center = KestrelShape.Eye + Offset(0.7f, -0.7f))
        drawPath(KestrelShape.Beak, Plumage.beak)
        drawCircle(Plumage.amber, radius = 1.35f, center = Offset(32.3f, -17.7f))
    }
}

/**
 * A long pointed falcon wing in its own frame: the span along +x from the shoulder, the leading edge towards +y and
 * the trailing edge towards -y (so a raised wing's trailing edge faces the tail). The far wing is a little darker.
 */
private fun DrawScope.drawWing(
    pose: BirdPose,
    near: Boolean,
) {
    // Folded, both wings lie along the flank and the far one hides behind the near one.
    val angle =
        when {
            near -> pose.wingAngle

            // Spread, the far wing shows on the other side of the body: both wings up, a V over the prize.
            pose.farWingAngle != null -> pose.farWingAngle

            else -> pose.wingAngle + lerp(-12f, -2f, pose.fold)
        }
    val s = 47f * pose.wingSpan * if (near) 1f else 0.93f
    val c = 15f * pose.wingChord * if (near) 1f else 0.9f
    val root =
        androidx.compose.ui.geometry
            .lerp(KestrelShape.Shoulder, KestrelShape.FlankRoot, pose.fold)
    val origin = if (near) root else root + Offset(-2.5f, -1.5f) * (1f - pose.fold)
    val shade = if (near) 1f else 0.78f
    withTransform({
        translate(origin.x, origin.y)
        rotate(angle, Offset.Zero)
    }) {
        val outline =
            Path().apply {
                moveTo(0f, 0.4f * c)
                cubicTo(0.25f * s, 0.55f * c, 0.68f * s, 0.38f * c, s, 0f)
                lineTo(0.86f * s, -0.2f * c)
                cubicTo(0.76f * s, -0.46f * c, 0.62f * s, -0.62f * c, 0.5f * s, -0.66f * c)
                cubicTo(0.35f * s, -0.7f * c, 0.15f * s, -0.72f * c, 0f, -0.6f * c)
                close()
            }
        drawPath(outline, Plumage.back.darken(shade))
        val primaries =
            Path().apply {
                moveTo(0.52f * s, 0.44f * c)
                cubicTo(0.7f * s, 0.38f * c, 0.86f * s, 0.22f * c, s, 0f)
                lineTo(0.86f * s, -0.2f * c)
                cubicTo(0.76f * s, -0.46f * c, 0.62f * s, -0.62f * c, 0.5f * s, -0.66f * c)
                quadraticTo(0.45f * s, -0.12f * c, 0.52f * s, 0.44f * c)
                close()
            }
        drawPath(primaries, Plumage.primaries.darken(shade))
        val secondaries =
            Path().apply {
                moveTo(0.5f * s, -0.66f * c)
                cubicTo(0.35f * s, -0.7f * c, 0.15f * s, -0.72f * c, 0f, -0.6f * c)
                lineTo(0f, -0.4f * c)
                cubicTo(0.15f * s, -0.5f * c, 0.35f * s, -0.48f * c, 0.5f * s, -0.44f * c)
                close()
            }
        drawPath(secondaries, Plumage.secondaries.darken(shade))
        // The primaries' edges, and the spotted coverts.
        for (index in 1..3) {
            val along = 0.6f + 0.1f * index
            drawLine(
                Color.White.copy(alpha = 0.16f * shade),
                Offset(along * s - 0.08f * s, (0.1f - 0.12f * index) * c),
                Offset(min(s, along * s + 0.14f * s), -0.06f * c * index),
                strokeWidth = 0.35f,
            )
        }
        for (spot in CovertSpots) {
            drawCircle(Plumage.spots.darken(shade), radius = 0.85f, center = Offset(spot.x * s, spot.y * c))
        }
    }
}

private val CovertSpots =
    listOf(
        Offset(0.12f, 0.05f),
        Offset(0.24f, -0.12f),
        Offset(0.34f, 0.14f),
        Offset(0.2f, 0.2f),
        Offset(0.4f, -0.18f),
        Offset(0.3f, -0.32f),
    )

private fun min(
    a: Float,
    b: Float,
): Float = if (a < b) a else b

/** The colour with its red, green and blue scaled by [factor] (1 is unchanged). */
internal fun Color.darken(factor: Float): Color =
    if (factor >= 1f) this else Color(red * factor, green * factor, blue * factor, alpha)

/** A wingbeat: [phase] 0 to 1 is one stroke. The wing sweeps from raised (0) down through the back to lowered. */
internal fun wingbeat(
    phase: Float,
    depth: Float = 1f,
): Pair<Float, Float> {
    val u = depth * (1f - cos(2f * PI.toFloat() * phase)) / 2f
    val angle = -105f - 140f * u
    // Pointing straight back the wing comes towards the viewer, so it looks short.
    val span = 0.42f + 0.58f * kotlin.math.abs(cos(PI.toFloat() * u))
    return angle to span
}
