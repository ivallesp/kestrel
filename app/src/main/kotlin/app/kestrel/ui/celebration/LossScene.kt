package app.kestrel.ui.celebration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.screens.result.ResultUiState
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin

/**
 * What the loss says under the scene. The plain fact stays plain ("YOU LOST", in the Ended state's colours); the
 * [headline] is the bird's ("It got away."), then the money as S4 tells it, and a [comfort] line with the rain. The
 * preview explains itself instead.
 */
@Immutable
data class LossContent(
    val title: String,
    val headline: String,
    val soldFor: String?,
    val yourMax: String?,
    val reason: String?,
    val comfort: String,
    val preview: Boolean,
) {
    companion object {
        /** Said after the miss. Declared first: the preview below uses it. */
        val HEADLINES = listOf("It got away.", "So close.", "Not this time.", "Next time.")

        /** From Settings: no snipe, no money, nothing bid; it only shows what a loss looks like. */
        val Preview =
            LossContent(
                title = "A lot that got away",
                headline = HEADLINES.first(),
                soldFor = null,
                yourMax = null,
                reason = null,
                comfort = LOSS_PREVIEW_NOTE,
                preview = true,
            )

        /** A confirmed loss: its title, "Sold for £104.00", "Your max £100.00" and why, as the result page says. */
        fun of(state: ResultUiState): LossContent {
            val figures = state.sold?.figures
            return LossContent(
                title = state.title,
                headline = HEADLINES[Math.floorMod(state.id, HEADLINES.size.toLong()).toInt()],
                soldFor = figures?.startValue?.takeIf { it.isNotEmpty() }?.let { "Sold for $it" },
                yourMax = figures?.endValue?.takeIf { it.isNotEmpty() }?.let { "Your max $it" },
                reason = state.sold?.reason,
                comfort = COMFORT,
                preview = false,
            )
        }
    }
}

/**
 * The loss, full screen over the result: the kestrel's scene above, the lost lot below (DESIGN 3.22). It plays once,
 * the first time a confirmed loss opens (about seven seconds); a tap on the scene skips to the end; with reduced
 * motion it opens on the last frame. [frameMillis] fixes the moment, for screenshots and tests. The preview ([content]
 * from [LossContent.Preview]) can play again; it never touches a snipe.
 */
@Composable
fun LossScene(
    content: LossContent,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    frameMillis: Long? = null,
) {
    val reduceMotion = KestrelTheme.reduceMotion
    var run by remember { mutableIntStateOf(0) }
    val clock = remember(run) { mutableLongStateOf(frameMillis ?: if (reduceMotion) LossTimeline.END else 0L) }
    val playing = frameMillis == null && !reduceMotion
    val haptics = LocalHapticFeedback.current
    val makeSound = LocalCelebrationSound.current
    // One sound for the screen, as for the win: the track starts on each run's first frame and stops when the scene is
    // skipped, closed or played again. A fixed frame is always silent.
    val sound = remember { if (frameMillis == null) makeSound() else null }
    DisposableEffect(sound) { onDispose { sound?.release() } }
    if (playing) {
        LaunchedEffect(run) {
            val start = withFrameMillis { it }
            sound?.play(SceneTrack.Loss)
            while (clock.longValue < LossTimeline.END) {
                withFrameMillis { frame ->
                    clock.longValue = maxOf(clock.longValue, (frame - start).coerceAtMost(LossTimeline.END))
                }
            }
        }
        LaunchedEffect(run) {
            // One flat tick as the talons close on nothing. A skipped scene has nothing to feel.
            snapshotFlow { clock.longValue }.first { it >= LossTimeline.MISS }
            if (clock.longValue < LossTimeline.END) haptics.performHapticFeedback(HapticFeedbackType.Reject)
        }
    }
    val time: () -> Long = if (frameMillis != null) ({ frameMillis }) else ({ clock.longValue })
    val ended by remember { derivedStateOf { time() >= LossTimeline.END } }
    val skip = {
        clock.longValue = LossTimeline.END
        sound?.stop()
    }
    BackHandler(onBack = onDone)
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val large = LocalDensity.current.fontScale > LARGE_TEXT
            val bandHeight = maxHeight * if (large) 0.38f else 0.47f
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(bandHeight)) {
                        LossBand(
                            time = time,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    // Keyed on the run, so a replay's clock is the one a tap moves.
                                    .pointerInput(run) { detectTapGestures { if (time() < LossTimeline.END) skip() } }
                                    .semantics {
                                        contentDescription = LOSS_DESCRIPTION
                                        if (!ended) {
                                            onClick(label = "Skip to the end") {
                                                skip()
                                                true
                                            }
                                        }
                                    },
                        )
                        IconButton(
                            onClick = onDone,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = sceneInk()),
                            modifier =
                                Modifier
                                    .windowInsetsPadding(
                                        windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Start),
                                    ).padding(4.dp),
                        ) {
                            SymbolIcon(Symbols.Close, contentDescription = "Close")
                        }
                    }
                    LossSheet(content = content, time = time, windowInsets = windowInsets)
                }
                CelebrationActions(
                    preview = content.preview,
                    onDone = onDone,
                    onPlayAgain =
                        {
                            run += 1
                            Unit
                        }.takeIf { content.preview && !reduceMotion && frameMillis == null },
                    windowInsets = windowInsets,
                )
            }
        }
    }
}

/** The loss's scene, drawn on every frame from [time] without recomposing anything around it. */
@Composable
internal fun LossBand(
    time: () -> Long,
    modifier: Modifier = Modifier,
) {
    val palette = if (KestrelTheme.isDark) ScenePalette.Dusk else ScenePalette.Morning
    val dark = KestrelTheme.isDark
    val measurer = rememberTextMeasurer()
    val tagStyle = KestrelTheme.type.smallCaps
    val callStyle = MaterialTheme.typography.displaySmall
    Box(
        // The scene's fence and grass run past its lower edge; the page below is shorter than the win's, so they would
        // show under it.
        modifier.clipToBounds().drawWithCache {
            val unit = SceneLayout(size).unit
            val tag =
                measurer.measure(
                    LOSS_TAG_WORD,
                    TextStyle(
                        fontFamily = tagStyle.fontFamily,
                        fontWeight = tagStyle.fontWeight,
                        letterSpacing = tagStyle.letterSpacing,
                        fontSize = (4.6f * unit).toSp(),
                    ),
                )
            val call =
                measurer.measure(
                    LOSS_CALL_WORD,
                    TextStyle(
                        fontFamily = callStyle.fontFamily,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        fontSize = (8f * unit).toSp(),
                    ),
                )
            onDrawBehind { drawLossScene(time(), palette, dark, tag, call) }
        },
    )
}

/**
 * Draws the loss at [timeMillis]: the win's morning turning grey, the clouds coming over the sun, the tag blown away,
 * the bird, the grass, the puff of the empty strike, the rain, the feather and the call.
 */
internal fun DrawScope.drawLossScene(
    timeMillis: Long,
    palette: ScenePalette,
    dark: Boolean,
    tagText: TextLayoutResult,
    callText: TextLayoutResult,
) {
    val t = timeMillis.coerceIn(0L, LossTimeline.END)
    val layout = SceneLayout(size)
    val u = layout.unit
    val rise = (1f - easeOut(CelebrationTimeline.progress(t, 0L, 700L))) * 18f * u
    val dim = LossTimeline.dim(t)
    val grey = if (dark) Color(0xFF070A12) else Color(0xFF4F5967)
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
    drawRainClouds(layout, palette, dark, t, rise)
    drawWind(layout, palette, t)
    translate(0f, rise * 0.5f) { drawFarHills(layout, palette) }
    translate(0f, rise) {
        drawMidHills(layout, palette)
        drawFence(layout, palette)
        drawMeadow(layout, palette)
    }
    // The day greys over the land first; the bird keeps a little more light than the hills.
    drawRect(grey.copy(alpha = (if (dark) 0.42f else 0.36f) * dim))
    val tag = LossTimeline.tag(t, size)
    if (tag?.lying == true) drawTag(TagState(tag.anchor, swing = 0f, lying = true), layout, palette, tagText)
    drawGust(layout, t)
    drawSpeedLines(layout, t)
    drawKestrel(LossTimeline.bird(t, size))
    if (tag != null && !tag.lying) {
        rotate(tag.spin, tag.anchor) {
            drawTag(TagState(tag.anchor, swing = 0f, lying = true), layout, palette, tagText)
        }
    }
    translate(0f, rise) { drawGrass(layout, palette, t) }
    drawStrikePuff(layout, palette, t)
    drawFeather(LossTimeline.feather(size), t, u)
    drawRect(grey.copy(alpha = 0.14f * dim))
    drawRain(layout, dark, t)
    drawLossCall(t, callText, palette)
}

/** Three heavy clouds come in from the right and close over the sun, which pales behind them. */
private fun DrawScope.drawRainClouds(
    layout: SceneLayout,
    palette: ScenePalette,
    dark: Boolean,
    t: Long,
    rise: Float,
) {
    val p = LossTimeline.clouds(t)
    if (p <= 0f) return
    drawCircle(
        palette.skyLow.copy(alpha = 0.85f * p),
        radius = layout.sunRadius * 1.04f,
        center = layout.sunCentre + Offset(0f, rise * 0.3f),
    )
    val w = size.width
    val h = size.height
    val body = if (dark) Color(0xFF4E5463) else Color(0xFFA2AAB5)
    val shade = if (dark) Color(0xFF3C414E) else Color(0xFF8E97A3)
    rainCloud(Offset(lerp(w * 1.25f, w * 0.46f, p), h * 0.22f), w * 0.44f, body, shade)
    rainCloud(Offset(lerp(w * 1.7f, w * 0.98f, p), h * 0.4f), w * 0.4f, body, shade)
    rainCloud(Offset(lerp(w * 1.4f, w * 0.72f, p), h * 0.55f), w * 0.5f, body, shade)
}

/** A heavy, puffy cloud: round tops on a flat, darker underside. */
private fun DrawScope.rainCloud(
    centre: Offset,
    width: Float,
    body: Color,
    shade: Color,
) {
    val base = width * 0.2f
    drawRoundRect(
        shade,
        topLeft = centre - Offset(width / 2f, 0f),
        size =
            androidx.compose.ui.geometry
                .Size(width, base),
        cornerRadius =
            androidx.compose.ui.geometry
                .CornerRadius(base / 2f),
    )
    for ((dx, dy, r) in listOf(
        Triple(-0.3f, 0.02f, 0.17f),
        Triple(-0.08f, -0.06f, 0.24f),
        Triple(0.18f, -0.02f, 0.2f),
        Triple(0.36f, 0.06f, 0.13f),
    )) {
        drawCircle(body, radius = width * r, center = centre + Offset(width * dx, width * dy))
    }
    drawRoundRect(
        body,
        topLeft = centre - Offset(width / 2f, base * 0.15f),
        size =
            androidx.compose.ui.geometry
                .Size(width, base * 0.75f),
        cornerRadius =
            androidx.compose.ui.geometry
                .CornerRadius(base / 2f),
    )
}

/** The gust that takes the tag: white streaks racing low over the grass, left to right. */
private fun DrawScope.drawGust(
    layout: SceneLayout,
    t: Long,
) {
    val p = CelebrationTimeline.progress(t, LossTimeline.GUST_FROM - 120L, LossTimeline.GUST_TO)
    if (p <= 0f || p >= 1f) return
    val u = layout.unit
    val fade = sin(PI.toFloat() * p)
    for (index in 0 until 4) {
        val y = layout.tagRest.y - (8f + index * 9f) * u
        val head = size.width * (-0.2f + 1.5f * p) - index * 18f * u
        drawLine(
            Color.White.copy(alpha = 0.45f * fade),
            Offset(head - 40f * u, y + 2f * u),
            Offset(head, y),
            strokeWidth = 0.8f * u,
            cap = StrokeCap.Round,
        )
    }
}

/** The rain: thin slanted drops, more of them as it sets in. Drawn from the time, so a fixed frame is a still. */
private fun DrawScope.drawRain(
    layout: SceneLayout,
    dark: Boolean,
    t: Long,
) {
    val amount = LossTimeline.rain(t)
    if (amount <= 0f) return
    val u = layout.unit
    val colour = if (dark) Color(0xFFB9C3D6) else Color(0xFFEEF2F7)
    val drops = (RAIN_DROPS * amount).toInt()
    for (index in 0 until drops) {
        val column = (index * 0.6180339f) % 1f
        val speed = 1f + ((index * 0.3711f) % 1f) * 0.5f
        val fall = ((index * 0.7548777f) % 1f + (t - LossTimeline.RAIN_FROM) / 700f * speed) % 1f
        val y = -0.05f * size.height + fall * size.height * 1.1f
        val x = column * size.width * 1.15f - 0.05f * size.width - y * 0.12f
        val length = (3.5f + (index % 3) * 1.2f) * u
        drawLine(
            colour.copy(alpha = 0.55f * amount),
            Offset(x, y),
            Offset(x - length * 0.12f, y + length),
            strokeWidth = 0.45f * u,
            cap = StrokeCap.Round,
        )
    }
}

/** One low "kee": it leaves the beak slowly, barely rises and fades. */
private fun DrawScope.drawLossCall(
    t: Long,
    text: TextLayoutResult,
    palette: ScenePalette,
) {
    val p = LossTimeline.call(t) ?: return
    val bird = LossTimeline.bird(t, size)
    val beak = bird.toScene(KestrelShape.Beak.getBounds().topRight)
    val u = SceneLayout(size).unit
    val alpha = 0.75f * (if (p < 0.25f) p / 0.25f else 1f - (p - 0.25f) / 0.75f)
    val topLeft = beak + Offset((10f + 6f * p) * u, -(8f + 6f * easeOut(p)) * u) - Offset(0f, text.size.height / 2f)
    drawText(text, color = palette.call.copy(alpha = alpha), topLeft = topLeft)
}

@Composable
private fun LossSheet(
    content: LossContent,
    time: () -> Long,
    windowInsets: WindowInsets,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    val typography = MaterialTheme.typography
    val accent = KestrelTheme.status.ended.accent
    Column(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val overlap = SHEET_OVERLAP.roundToPx()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, -overlap) }
            }.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
            .padding(start = Spacing.ColumnA, end = Spacing.ColumnA, top = 24.dp, bottom = Spacing.Large),
    ) {
        // The words come in beats with the scene: the loss as the bird hovers, its words after the miss, the money
        // after them, and the comfort with the rain.
        Column(Modifier.reveal(time, LossTimeline.TITLE_FROM)) {
            if (content.preview) {
                SmallCaps("Preview")
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SymbolIcon(Symbols.Flag, tint = accent, size = 18.dp)
                    SmallCaps("You lost", color = accent)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            content.headline,
            style = typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.reveal(time, LossTimeline.HEADLINE_FROM).semantics { heading() },
        )
        Column(Modifier.reveal(time, LossTimeline.TITLE_FROM)) {
            Spacer(Modifier.height(2.dp))
            Text(
                content.title,
                style = KestrelTheme.type.lotTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.reveal(time, LossTimeline.MONEY_FROM)) {
            content.soldFor?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = KestrelTheme.type.priceRow, color = MaterialTheme.colorScheme.onBackground)
            }
            content.yourMax?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = typography.titleMedium, color = inkSoft)
            }
            content.reason?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = typography.bodyMedium, color = inkSoft)
            }
        }
        val from = if (content.preview) LossTimeline.TITLE_FROM else LossTimeline.COMFORT_FROM
        Column(Modifier.reveal(time, from)) {
            Spacer(Modifier.height(10.dp))
            Text(
                content.comfort,
                style = if (content.preview) typography.bodyMedium else typography.titleMedium,
                color = if (content.preview) inkSoft else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** Settings' preview of the loss (Destination.LossPreview): sample words only, nothing bid or stored. */
@Composable
fun LossPreviewDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    LossScene(LossContent.Preview, onDone = navigator::back, modifier = modifier, windowInsets = windowInsets)
}

private const val RAIN_DROPS = 90

/** The tag the gust takes: the lot went to someone else. */
private const val LOSS_TAG_WORD = "SOLD"

private const val LOSS_CALL_WORD = "kee…"

private const val LOSS_DESCRIPTION =
    "A kestrel dives at your lot’s tag in the grass, but a gust blows the tag away and its talons close on nothing. " +
        "It flies back to its fence post, hunches as the sky clouds over and the rain comes, and calls once, softly."

private const val LOSS_PREVIEW_NOTE = "This plays the first time you open a loss. Nothing was bid."

private const val COMFORT = "You pay nothing. Kestrel is ready for the next one."
