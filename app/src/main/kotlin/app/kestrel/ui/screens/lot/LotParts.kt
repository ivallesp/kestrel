package app.kestrel.ui.screens.lot

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.CalmLoadingIndicator
import app.kestrel.ui.components.Figure
import app.kestrel.ui.components.OutlinedActionButton
import app.kestrel.ui.components.RollingDigits
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.noWidow
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing
import java.util.Locale

/**
 * The countdown row (DESIGN S3 3): the countdown at the start and the times column at the end, their last baselines
 * aligned. When both do not fit (large text), the times column moves under the countdown.
 */
@Composable
fun CountdownRow(
    countdown: CountdownText,
    times: TimesColumnText,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            CountdownFigure(countdown)
            TimesColumn(times)
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val figure = measurables[0].measure(loose)
        val column = measurables[1].measure(loose)
        val gap = 12.dp.roundToPx()
        val width = constraints.maxWidth
        if (figure.width + gap + column.width <= width) {
            val figureBaseline = figure[LastBaseline].takeIf { it != AlignmentLine.Unspecified } ?: figure.height
            val columnBaseline = column[LastBaseline].takeIf { it != AlignmentLine.Unspecified } ?: column.height
            val baseline = maxOf(figureBaseline, columnBaseline)
            val figureTop = baseline - figureBaseline
            val columnTop = baseline - columnBaseline
            val height = maxOf(figureTop + figure.height, columnTop + column.height)
            layout(width, height) {
                figure.place(0, figureTop)
                column.place(width - column.width, columnTop)
            }
        } else {
            layout(width, figure.height + gap + column.height) {
                figure.place(0, 0)
                column.place(0, figure.height + gap)
            }
        }
    }
}

/**
 * "36 s left" in the final minute, "13 m 07 s left" before it; only changed digits roll, and the unit letters are set
 * in the unit style, never at figure size. Words alone ("Ended") take the countdown unit style. One spoken sentence.
 */
@Composable
private fun CountdownFigure(countdown: CountdownText) {
    val ink = MaterialTheme.colorScheme.onSurface
    val type = KestrelTheme.type
    val unitStyle = if (countdown.finalMinute) type.countdownUnit else MaterialTheme.typography.headlineMedium
    Row(Modifier.clearAndSetSemantics { contentDescription = countdown.spoken }) {
        if (countdown.figure.isNotEmpty()) {
            RollingDigits(
                text = countdown.figure,
                style = if (countdown.finalMinute) type.countdown else type.countdownMedium,
                unitStyle = MaterialTheme.typography.headlineMedium,
                color = ink,
                modifier = Modifier.alignBy(LastBaseline),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = countdown.unit,
            style = unitStyle,
            color = ink,
            maxLines = 1,
            modifier = Modifier.alignBy(LastBaseline),
        )
    }
}

/** The times column (from C2): the day and zone, then "BIDS", "ENDS" and a ticking "NOW", labels in a shared column. */
@Composable
private fun TimesColumn(times: TimesColumnText) {
    val labelStyle = KestrelTheme.type.smallCaps
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth =
        remember(times.rows, labelStyle, density) {
            val widest =
                times.rows.maxOfOrNull { row ->
                    measurer.measure(row.label.uppercase(Locale.UK), labelStyle, maxLines = 1).size.width
                } ?: 0
            maxOf(TimesLabelWidth, with(density) { widest.toDp() } + 8.dp)
        }
    val ink = MaterialTheme.colorScheme.onSurface
    val figureStyle = MaterialTheme.typography.titleMedium
    // As wide as its widest row and placed at the end edge, so the figures end on the 395 dp column.
    Column(Modifier.clearAndSetSemantics { contentDescription = times.spoken }) {
        SmallCaps(times.heading)
        times.rows.forEach { row ->
            Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallCaps(row.label, modifier = Modifier.width(labelWidth).alignByBaseline())
                if (row.ticks) {
                    RollingDigits(row.time, style = figureStyle, color = ink, modifier = Modifier.alignByBaseline())
                } else {
                    Figure(row.time, style = figureStyle, color = ink, modifier = Modifier.alignByBaseline())
                }
            }
        }
        times.note?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private val TimesLabelWidth = 44.dp

/**
 * The final-minute scale (DESIGN 3.12), the app's only wavy element: the wave in the Bidding accent fills the minute,
 * the remaining time is a 2 dp outline track ending in an ink stop, and an ink tick marks the bid. The wave grows in
 * at 60 s left and flattens once the bid is sent; with reduced motion it is a flat, still line.
 */
@Composable
fun FinalMinuteScale(
    scale: FinalMinuteScaleState,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = KestrelTheme.reduceMotion
    val motion = MaterialTheme.motionScheme
    val bidding = KestrelTheme.status.bidding
    val ink = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline
    val progressTarget = scale.elapsedSeconds / FINAL_MINUTE_SECONDS
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = if (reduceMotion) snap() else ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "final minute",
    )
    val amplitudeTarget = if (reduceMotion || scale.flat) 0f else 1f
    val amplitudeSpec: AnimationSpec<Float> =
        when {
            reduceMotion -> snap()
            amplitudeTarget == 0f -> motion.defaultEffectsSpec()
            else -> motion.slowEffectsSpec()
        }
    val amplitude by animateFloatAsState(
        targetValue = amplitudeTarget,
        animationSpec = amplitudeSpec,
        label = "wave amplitude",
    )
    val density = LocalDensity.current
    val waveStroke = with(density) { Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round) }
    val trackStroke = with(density) { Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round) }
    val labelStyle = MaterialTheme.typography.labelMedium
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = scale.spoken
                progressBarRangeInfo = ProgressBarRangeInfo(progressTarget, 0f..1f)
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(ScaleHeight)) {
            val trackWidth = maxWidth
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                color = bidding.accent,
                trackColor = outline,
                stroke = waveStroke,
                trackStroke = trackStroke,
                stopSize = 0.dp,
                amplitude = { amplitude },
                wavelength = WaveLength,
                waveSpeed = if (reduceMotion) 0.dp else WaveLength / 2,
            )
            // The end: the stop indicator in ink.
            Canvas(Modifier.fillMaxWidth().height(ScaleHeight)) {
                val radius = WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize.toPx() / 2
                drawCircle(ink, radius, Offset(size.width - radius, size.height / 2))
            }
            scale.bidFraction?.let { fraction ->
                Box(
                    Modifier
                        .offset(x = trackWidth * fraction - BidTickWidth / 2)
                        .width(BidTickWidth)
                        .height(ScaleHeight)
                        .background(ink, RoundedCornerShape(BidTickWidth / 2)),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        ScaleLabels(scale, labelStyle, inkSoft, ink)
    }
}

/** "Final minute" at the start, "Bid" ending 6 dp before the tick, "End" at the end; "End" gives way when crowded. */
@Composable
private fun ScaleLabels(
    scale: FinalMinuteScaleState,
    style: androidx.compose.ui.text.TextStyle,
    inkSoft: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
) {
    Layout(
        content = {
            Text(scale.startLabel, style = style, color = inkSoft, maxLines = 1)
            Text("Bid", style = style, color = ink, maxLines = 1)
            Text("End", style = style, color = inkSoft, maxLines = 1)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val start = measurables[0].measure(loose)
        val bid = measurables[1].measure(loose)
        val end = measurables[2].measure(loose)
        val width = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val tickX = scale.bidFraction?.let { (width * it).toInt() }
        val bidX = tickX?.let { it - 6.dp.roundToPx() - bid.width }
        val showBid = bidX != null && bidX >= start.width + gap
        val endX = width - end.width
        val showEnd = !showBid || (bidX + bid.width + gap <= endX)
        val height = maxOf(start.height, bid.height, end.height)
        layout(width, height) {
            start.place(0, 0)
            if (showBid) bid.place(bidX, 0)
            if (showEnd) end.place(endX, 0)
        }
    }
}

private const val FINAL_MINUTE_SECONDS = 60f
private val ScaleHeight = 24.dp

/** How tall the scale's track is, above its labels. */
internal val FinalMinuteScaleTrackHeight: Dp = ScaleHeight
private val BidTickWidth = 3.dp
private val WaveLength = 28.dp

/**
 * The status card (DESIGN 3.13): the Bidding container for the bid, Attention for a problem, the red Failed tone only
 * when the bid was not sent or failed. A glyph (or the waiting indicator), two lines, and the status's action.
 */
@Composable
fun StatusCardView(
    card: StatusCard,
    onAction: (LotAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KestrelTheme.status[card.tone]
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val glyphTop =
        with(LocalDensity.current) { ((titleStyle.lineHeight.toDp() - CardGlyphSize) / 2).coerceAtLeast(0.dp) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = glyphTop)) {
                    when (card.glyph) {
                        CardGlyph.Gavel -> SymbolIcon(Symbols.Gavel, tint = colors.accent, size = CardGlyphSize)
                        CardGlyph.Waiting -> CalmLoadingIndicator(color = colors.accent, size = CardGlyphSize)
                        CardGlyph.Warning -> SymbolIcon(Symbols.Warning, tint = colors.accent, size = CardGlyphSize)
                        CardGlyph.Error -> SymbolIcon(Symbols.Error, tint = colors.accent, size = CardGlyphSize)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.title, style = titleStyle, color = colors.onContainer)
                    Text(card.detail.noWidow(), style = MaterialTheme.typography.bodyMedium, color = colors.onContainer)
                }
            }
            card.action?.let { action ->
                Spacer(Modifier.height(12.dp))
                Row {
                    Spacer(Modifier.width(CardGlyphSize + 12.dp))
                    InkButton(action.label, onClick = { onAction(action) })
                }
            }
        }
    }
}

private val CardGlyphSize = 24.dp

/** A filled ink button, 40 dp visual inside a 48 dp touch target (DESIGN 3.17: actions are ink). */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val height = Spacing.InlineButtonHeight
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        modifier = modifier.heightIn(min = height),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** "Edit max" (48 dp, outlined) and its deadline, or the locked sentence once the edits closed. */
@Composable
fun EditRow(
    edit: EditRowState,
    onAction: (LotAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedActionButton(
            text = edit.action.label,
            onClick = { onAction(edit.action) },
            icon = Symbols.Edit,
            height = Spacing.TouchTarget,
            enabled = edit.enabled,
        )
        edit.note?.let { note ->
            Spacer(Modifier.width(12.dp))
            Text(
                text = note.noWidow(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "CHECKS PASSED" on a card: one check_circle in the Won accent per check that passed for this lot. */
@Composable
fun ChecksCard(
    checks: List<String>,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyMedium
    val iconTop = with(LocalDensity.current) { ((style.lineHeight.toDp() - 20.dp) / 2).coerceAtLeast(0.dp) }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = KestrelTheme.catalogue.card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallCaps("Checks passed")
            checks.forEach { check ->
                Row(verticalAlignment = Alignment.Top) {
                    SymbolIcon(
                        Symbols.CheckCircle,
                        tint = KestrelTheme.status.won.accent,
                        size = 20.dp,
                        modifier = Modifier.padding(top = iconTop),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(check, style = style, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
