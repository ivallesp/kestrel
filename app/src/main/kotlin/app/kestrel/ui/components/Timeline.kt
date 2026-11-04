package app.kestrel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.StatusTone
import app.kestrel.ui.theme.tabular

/** Where an entry sits relative to now. Future entries sit above "Now" (newest first). */
enum class TimelineMoment { Past, Now, Future }

/**
 * One row of a timeline or receipt (DESIGN 3.10). [time] is already formatted ("18:21:33.9" in receipts, "18:21:34"
 * in S3); [tone] gives a key past event its family accent (Bid sent in Bidding, Won confirmed in Won, failures in
 * Failed); [spoken] is the row's TalkBack sentence ("18:21:33.9, bid of £120.00 sent, 6.1 seconds before the end").
 */
@Immutable
data class TimelineEntry(
    val time: String,
    val title: String,
    val detail: String? = null,
    val moment: TimelineMoment = TimelineMoment.Past,
    val tone: StatusTone? = null,
    val spoken: String? = null,
)

/** Rows have 8 dp above the title; the dot sits on the middle of its first line. */
private val RowTopPadding = 8.dp
private val PastDotRadius = 4.dp
private val NowDotRadius = 5.dp
private val NowHaloRadius = 10.dp
private val FutureDotRadius = 5.dp
private val RingStroke = 2.dp
private val RailWidth = 2.dp

/** The least room between the widest time and the rail. */
private val TimeGap = 8.dp

/**
 * A receipt-style timeline, newest at the top (DESIGN 3.10). Past events sit on a solid rail
 * in the rule colour with filled dots; "Now" is a Bidding dot on a halo; from now up to the future events the rail is
 * dashed in the outline colour with hollow dots, so what has happened and what will happen never blur.
 *
 * The time column is [timeColumnWidth] wide. With large text it grows to fit the widest time, so "18:21:33.9" never
 * breaks inside the figure and the text column keeps the rest; a day and a time ("Wed 5 Nov 21:12:44") wrap.
 */
@Composable
fun Timeline(
    entries: List<TimelineEntry>,
    modifier: Modifier = Modifier,
    timeColumnWidth: Dp = 96.dp,
) {
    val timeStyle = MaterialTheme.typography.bodyMedium.tabular()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val columnWidth =
        remember(entries, timeStyle, density, timeColumnWidth) {
            val widestWord =
                entries
                    .flatMap { it.time.split(' ') }
                    .maxOfOrNull { word ->
                        measurer.measure(word, timeStyle, softWrap = false, maxLines = 1).size.width
                    }
                    ?: 0
            maxOf(timeColumnWidth, with(density) { widestWord.toDp() } + TimeGap)
        }
    Column(modifier) {
        entries.forEachIndexed { index, entry ->
            TimelineRow(
                entry = entry,
                railAbove = entries.getOrNull(index - 1)?.let { railBetween(upper = it) },
                railBelow = if (index < entries.lastIndex) railBetween(upper = entry) else null,
                timeColumnWidth = columnWidth,
            )
        }
    }
}

private enum class Rail { Solid, Dashed }

/** The rail is dashed from now up to the future, solid between past events. */
private fun railBetween(upper: TimelineEntry): Rail =
    if (upper.moment ==
        TimelineMoment.Future
    ) {
        Rail.Dashed
    } else {
        Rail.Solid
    }

@Composable
private fun TimelineRow(
    entry: TimelineEntry,
    railAbove: Rail?,
    railBelow: Rail?,
    timeColumnWidth: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val inkSoft = scheme.onSurfaceVariant
    val bidding = KestrelTheme.status.bidding
    val ruleColour = KestrelTheme.catalogue.rule
    val outline = scheme.outline
    val pastDot = entry.tone?.let { KestrelTheme.status[it].accent } ?: inkSoft
    val future = entry.moment == TimelineMoment.Future
    val spoken = entry.spoken ?: listOfNotNull(entry.time, entry.title, entry.detail).joinToString(", ")
    val titleStyle = MaterialTheme.typography.bodyLarge
    val density = LocalDensity.current
    val dotCentre = with(density) { RowTopPadding + titleStyle.lineHeight.toDp() / 2 }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 40.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Column(Modifier.width(timeColumnWidth).alignBy(FirstBaseline).padding(vertical = 8.dp)) {
            if (entry.moment == TimelineMoment.Now) {
                Text(
                    text = "Now",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = bidding.accent,
                )
            }
            Figure(text = entry.time, style = MaterialTheme.typography.bodyMedium, color = inkSoft, maxLines = 2)
        }
        Canvas(Modifier.width(24.dp).fillMaxHeight()) {
            val x = size.width / 2
            val dotY = dotCentre.toPx()
            val dotRadius =
                when (entry.moment) {
                    TimelineMoment.Past -> PastDotRadius
                    TimelineMoment.Now -> NowHaloRadius
                    TimelineMoment.Future -> FutureDotRadius
                }.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))

            fun rail(
                kind: Rail,
                from: Float,
                to: Float,
            ) {
                if (to <= from) return
                drawLine(
                    color = if (kind == Rail.Dashed) outline else ruleColour,
                    start = Offset(x, from),
                    end = Offset(x, to),
                    strokeWidth = RailWidth.toPx(),
                    pathEffect = if (kind == Rail.Dashed) dash else null,
                )
            }
            railAbove?.let { rail(it, 0f, dotY - dotRadius) }
            railBelow?.let { rail(it, dotY + dotRadius, size.height) }
            when (entry.moment) {
                TimelineMoment.Past -> {
                    drawCircle(pastDot, PastDotRadius.toPx(), Offset(x, dotY))
                }

                TimelineMoment.Now -> {
                    drawCircle(bidding.container, NowHaloRadius.toPx(), Offset(x, dotY))
                    drawCircle(bidding.accent, NowDotRadius.toPx(), Offset(x, dotY))
                }

                TimelineMoment.Future -> {
                    val stroke = RingStroke.toPx()
                    drawCircle(outline, FutureDotRadius.toPx() - stroke / 2, Offset(x, dotY), style = Stroke(stroke))
                }
            }
        }
        Column(Modifier.weight(1f).alignBy(FirstBaseline).padding(vertical = 8.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (future) inkSoft else scheme.onSurface,
            )
            entry.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = inkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
