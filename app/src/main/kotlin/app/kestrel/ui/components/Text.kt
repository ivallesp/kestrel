package app.kestrel.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.kestrel.BuildConfig
import app.kestrel.ui.format.NO_BREAK_SPACE
import app.kestrel.ui.format.ZERO_WIDTH_SPACE
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.TABULAR_FIGURES
import app.kestrel.ui.theme.tabular
import java.util.Locale

/** The small-caps rules of DESIGN SC1 and SC2. */
object SmallCapsRules {
    const val MAXIMUM_LENGTH = 32

    // A digit followed by a unit ("13 m", "6s", "13\u202Fm"): units are never set in capitals.
    private val numberAndUnit = Regex("""\d[\s\u00A0\u202F]?[smhd]\b""")

    /** Data labels and overlines only: at most 32 characters, no units and never the word eBay. */
    fun allows(text: String): Boolean =
        text.length <= MAXIMUM_LENGTH && !numberAndUnit.containsMatchIn(text) &&
            !text.contains("eBay", ignoreCase = true)
}

/**
 * Letter-spaced capitals for data labels above figures ("CURRENT · 14 BIDS", "YOUR MAX"), overlines and section
 * heads. Debug builds reject a sentence, a unit or the word eBay (rule SC2), so the mistake never ships.
 */
@Composable
fun SmallCaps(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = KestrelTheme.type.smallCaps,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
) {
    if (BuildConfig.DEBUG) require(SmallCapsRules.allows(text)) { "Not a small-caps label: “$text”" }
    Text(
        text = text.uppercase(Locale.UK),
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A catalogue section head (DESIGN 3.7): serif small caps in ink, a 1 dp rule filling the space, and the [count]
 * ("2 lots") in ink soft. It is a heading for TalkBack. Place it from 16 dp to 395 dp.
 */
@Composable
fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
) {
    Row(
        modifier =
            modifier.semantics(mergeDescendants = true) {
                heading()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallCaps(title, color = MaterialTheme.colorScheme.onSurface, style = KestrelTheme.type.sectionHead)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = KestrelTheme.catalogue.rule)
        if (count != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Prepares figure text for tabular figures (rule T3). Google Sans Flex's tnum widens the word space, so spaces become
 * no-break spaces, which tnum leaves alone. Text that may wrap gets a zero-width break opportunity after each one.
 */
fun tabularText(
    text: String,
    wraps: Boolean,
): String {
    val replacement = if (wraps) "$NO_BREAK_SPACE$ZERO_WIDTH_SPACE" else NO_BREAK_SPACE.toString()
    return text.replace(" ", replacement)
}

/**
 * Figures-only sans text: clock times, ledger values, timeline times, counts, the S3 times column (rule T3). Tabular
 * figures are set on the whole string; never pass words.
 */
@Composable
fun Figure(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
) {
    Text(
        text = tabularText(text, wraps = maxLines > 1),
        style = style.tabular(),
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
    )
}

/** The span on a ticking or aligned figure inside a sentence (rule T4). It sets tabular figures and nothing else. */
val TabularFigureSpan = SpanStyle(fontFeatureSettings = TABULAR_FIGURES)

/** Appends [figure] with tabular figures, its spaces made no-break so tnum does not widen them. */
fun AnnotatedString.Builder.appendFigure(figure: String) {
    withStyle(TabularFigureSpan) { append(tabularText(figure, wraps = false)) }
}

/**
 * A sentence with one ticking figure: `figureSentence("Ends in ", "13 m 07 s")`. Static prices inside sentences keep
 * proportional figures (rule T6), so only pass the part that ticks or lines up.
 */
fun figureSentence(
    before: String,
    figure: String,
    after: String = "",
): AnnotatedString =
    buildAnnotatedString {
        append(before)
        appendFigure(figure)
        append(after)
    }
