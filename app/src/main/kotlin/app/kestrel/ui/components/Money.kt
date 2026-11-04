package app.kestrel.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kestrel.model.Money
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.plural
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.StatusTone

/** How much of the max the price has used, for [HeadroomRule]. [tone] colours the fill. */
@Immutable
data class Headroom(
    val current: Money,
    val max: Money,
    val tone: StatusTone,
)

/** A caption under the money grid. [overMax] sets it in the Attention accent at weight 600. */
@Immutable
data class MoneyCaption(
    val text: String,
    val overMax: Boolean = false,
    @param:DrawableRes val icon: Int? = null,
)

/**
 * The content of a [MoneyBlock]: two labelled figures side by side, an optional note under the end figure, an
 * optional headroom rule and its captions, and one spoken sentence for the whole block.
 */
@Immutable
data class MoneyFigures(
    val startLabel: String,
    val startValue: String,
    val endLabel: String,
    val endValue: String,
    val spoken: String,
    val note: String? = null,
    val headroom: Headroom? = null,
    val startCaption: MoneyCaption? = null,
    val endCaption: MoneyCaption? = null,
)

/**
 * The money block of a live lot (hero, S3): the current price against the max, the worst case with postage, the
 * headroom rule and its captions. With [previousPrice] the start caption becomes the price change ("Up £8.00 from
 * £88.00") and the headroom moves to the end caption, as on S3.
 */
fun lotMoneyFigures(
    current: Money,
    bidCount: Int,
    max: Money,
    postage: Money?,
    tone: StatusTone,
    previousPrice: Money? = null,
): MoneyFigures {
    val comparable = current.currency == max.currency
    val over = comparable && current.minor > max.minor
    val headroomText =
        when {
            !comparable -> null
            over -> "${(current - max).formatted()} over your max"
            current == max -> "At your max"
            else -> "${(max - current).formatted()} below your max"
        }
    val headroomCaption = headroomText?.let { MoneyCaption(it, overMax = over) }
    val worstCaseAmount = postage?.takeIf { it.currency == max.currency }?.let { max + it }
    val worstCase = worstCaseAmount?.let { "At most ${it.formatted()} with postage" }
    val risen =
        previousPrice?.takeIf { it.currency == current.currency && it.minor < current.minor }
    val startLabel = if (bidCount == 0) "Starting price" else "Current · ${plural(bidCount, "bid", "bids")}"
    val spokenBids = if (bidCount == 0) "Starting price" else "Current bid"
    val spoken =
        buildString {
            append("$spokenBids ${current.formatted()}")
            if (bidCount > 0) append(", ${plural(bidCount, "bid", "bids")}")
            append(". Your max ${max.formatted()}")
            worstCaseAmount?.let { append(", at most ${it.formatted()} with postage") }
            append('.')
            risen?.let { append(" Up ${(current - it).formatted()} from ${it.formatted()}.") }
            headroomText?.let { append(" $it.") }
        }
    return MoneyFigures(
        startLabel = startLabel,
        startValue = current.formatted(),
        endLabel = "Your max",
        endValue = max.formatted(),
        spoken = spoken,
        note = worstCase,
        headroom = Headroom(current, max, if (over) StatusTone.Attention else tone).takeIf { comparable },
        startCaption =
            if (risen != null) {
                MoneyCaption("Up ${(current - risen).formatted()} from ${risen.formatted()}", icon = Symbols.TrendingUp)
            } else {
                headroomCaption
            },
        endCaption =
            if (risen != null) {
                headroomCaption
            } else {
                MoneyCaption(postage?.let { "Postage ${it.formatted()}" } ?: "Postage not listed")
            },
    )
}

/**
 * The money grid (DESIGN 3.2): labels in small caps on one shared baseline, the start figure in priceHero and the end
 * figure in priceLarge on another, an optional end-aligned note, then the headroom rule and the caption row. One
 * merged node for TalkBack. Without [MoneyFigures.headroom] it is the plain grid of S2 ("POSTAGE") and S4
 * ("SOLD FOR").
 */
@Composable
fun MoneyBlock(
    figures: MoneyFigures,
    modifier: Modifier = Modifier,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.clearAndSetSemantics { contentDescription = figures.spoken }) {
        Row(Modifier.fillMaxWidth()) {
            SmallCaps(figures.startLabel, modifier = Modifier.weight(1f).alignBy(FirstBaseline))
            Spacer(Modifier.width(12.dp))
            SmallCaps(figures.endLabel, modifier = Modifier.alignBy(FirstBaseline))
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            PriceFigure(
                text = figures.startValue,
                style = KestrelTheme.type.priceHero,
                modifier = Modifier.weight(1f).alignBy(LastBaseline),
            )
            Spacer(Modifier.width(12.dp))
            PriceFigure(
                text = figures.endValue,
                style = KestrelTheme.type.priceLarge,
                modifier = Modifier.alignBy(LastBaseline),
            )
        }
        figures.note?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = inkSoft,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        figures.headroom?.let { headroom ->
            Spacer(Modifier.height(8.dp))
            HeadroomRule(
                current = headroom.current,
                max = headroom.max,
                fill = KestrelTheme.status[headroom.tone].accent,
            )
        }
        if (figures.startCaption != null || figures.endCaption != null) {
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                figures.startCaption?.let { MoneyCaptionText(it, Modifier.weight(1f, fill = false)) }
                Spacer(Modifier.width(12.dp))
                figures.endCaption?.let { MoneyCaptionText(it, Modifier.weight(1f, fill = false), TextAlign.End) }
            }
        }
    }
}

@Composable
private fun MoneyCaptionText(
    caption: MoneyCaption,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val color =
        if (caption.overMax) KestrelTheme.status.attention.accent else MaterialTheme.colorScheme.onSurfaceVariant
    val style =
        if (caption.overMax) {
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.bodyMedium
        }
    // The icon sits on the middle of the first line when a long caption wraps.
    val iconTop = with(LocalDensity.current) { ((style.lineHeight.toDp() - CaptionIconSize) / 2).coerceAtLeast(0.dp) }
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        caption.icon?.let {
            SymbolIcon(it, tint = color, size = CaptionIconSize, modifier = Modifier.padding(top = iconTop))
            Spacer(Modifier.width(4.dp))
        }
        // Captions wrap rather than clip at large font sizes.
        Text(text = caption.text, style = style, color = color, textAlign = textAlign)
    }
}

/**
 * A serif price on one line that steps its size down rather than clip when the space is short (a large amount at a
 * large font scale); its style carries tabular figures.
 */
@Composable
private fun PriceFigure(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = MINIMUM_PRICE_SIZE, maxFontSize = style.fontSize),
    )
}

private val MINIMUM_PRICE_SIZE = 16.sp

private val CaptionIconSize = 16.dp

/**
 * The headroom rule (DESIGN 3.2): a 4 dp track in the rule colour, the used part of the max in [fill], and a 2 x 12 dp
 * ink tick at the max. Past the max the fill covers the track and the tick moves to max divided by current. The fill
 * eases to a new price (defaultEffectsSpec, no overshoot).
 */
@Composable
fun HeadroomRule(
    current: Money,
    max: Money,
    fill: Color,
    modifier: Modifier = Modifier,
) {
    val over = current.minor > max.minor
    val fillTarget = if (over || max.minor <= 0) 1f else current.minor.toFloat() / max.minor
    val tickTarget = if (over) max.minor.toFloat() / current.minor else 1f
    val fillFraction by animateFloatAsState(
        fillTarget,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "headroom",
    )
    val track = KestrelTheme.catalogue.rule
    val tick = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val thickness = 4.dp.toPx()
        val tickWidth = 2.dp.toPx()
        val y = size.height / 2
        val inset = thickness / 2
        val usable = size.width - thickness
        drawLine(track, Offset(inset, y), Offset(inset + usable, y), thickness, StrokeCap.Round)
        if (fillFraction > 0f) {
            drawLine(fill, Offset(inset, y), Offset(inset + usable * fillFraction, y), thickness, StrokeCap.Round)
        }
        val tickX = (inset + usable * tickTarget).coerceIn(tickWidth / 2, size.width - tickWidth / 2)
        drawLine(tick, Offset(tickX, 0f), Offset(tickX, size.height), tickWidth)
    }
}

/**
 * A ledger line (DESIGN 3.11): the label, a dotted leader of 1 dp dots every 5 dp on the baseline, and the value as a
 * figure. [total] adds 4 dp above and sets both sides in titleMedium at weight 700.
 *
 * The label and the value align by baseline: a line of digits gets a shorter box than a line of words, so aligning
 * their tops would set the value higher than the label.
 */
@Composable
fun LedgerRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    total: Boolean = false,
) {
    val style =
        if (total) {
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        } else {
            MaterialTheme.typography.bodyLarge
        }
    val ink = MaterialTheme.colorScheme.onSurface
    val dots = KestrelTheme.catalogue.inkSoft.copy(alpha = 0.55f)
    // The label's baseline inside the row: its own baseline plus where the row placed it.
    val labelTop = remember { mutableFloatStateOf(0f) }
    val labelBaseline = remember { mutableFloatStateOf(0f) }
    Row(modifier = modifier.padding(top = if (total) 4.dp else 0.dp).height(IntrinsicSize.Min)) {
        Text(
            text = label,
            style = style,
            color = ink,
            maxLines = 1,
            onTextLayout = { labelBaseline.floatValue = it.firstBaseline },
            modifier =
                Modifier
                    .alignByBaseline()
                    .onPlaced { labelTop.floatValue = it.positionInParent().y },
        )
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
        ) {
            val step = 5.dp.toPx()
            val radius = 0.5.dp.toPx()
            val y = labelTop.floatValue + labelBaseline.floatValue - radius
            var x = radius
            while (x < size.width - radius) {
                drawCircle(dots, radius, Offset(x, y))
                x += step
            }
        }
        Figure(text = value, style = style, color = ink, modifier = Modifier.alignByBaseline())
    }
}

/**
 * The sum row (DESIGN 3.11): "YOUR MAX + POSTAGE = YOU PAY AT MOST", with the total at weight 700, then the proxy
 * sentence. Labels share one baseline and values another; the operators sit on the values' baseline.
 */
@Composable
fun SumRow(
    max: String,
    postage: String,
    total: String,
    modifier: Modifier = Modifier,
) {
    val spoken = "Your max $max plus postage $postage: you pay at most $total."
    Column(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Row(Modifier.fillMaxWidth()) {
            val price = KestrelTheme.type.priceRow
            SumColumn("Your max", max, price, Modifier.weight(1f).alignBy(LastBaseline))
            Operator("+", Modifier.alignBy(LastBaseline))
            SumColumn("Postage", postage, price, Modifier.weight(0.9f).alignBy(LastBaseline))
            Operator("=", Modifier.alignBy(LastBaseline))
            SumColumn(
                "You pay at most",
                total,
                KestrelTheme.type.priceRowTotal,
                Modifier.weight(1.3f).alignBy(LastBaseline),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Usually less: eBay charges one step above the next bidder, never more than your max.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SumColumn(
    label: String,
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SmallCaps(label, maxLines = 2)
        Text(text = value, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun Operator(
    symbol: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = symbol,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.width(24.dp),
    )
}
