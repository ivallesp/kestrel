package app.kestrel.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.kestrel.ui.model.StateWords
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.StatusTone

/**
 * A state label (DESIGN 3.1): the family glyph at 18 dp and the state words in the family accent, then " · " and the
 * detail in ink soft. The words never truncate; a detail that does not fit wraps under them and ellipsises on the
 * second line. [large] uses stateWordLarge, for the S4 status line and the S5 rows.
 */
@Composable
fun StateLabel(
    tone: StatusTone,
    words: StateWords,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val accent by animateColorAsState(
        targetValue = KestrelTheme.status[tone].accent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "state accent",
    )
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    val wordStyle = if (large) KestrelTheme.type.stateWordLarge else KestrelTheme.type.stateWord
    val detailStyle = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
    // The glyph is centred on the first line, whatever the font scale makes of the line height.
    val glyphTop = with(LocalDensity.current) { ((wordStyle.lineHeight.toDp() - GlyphSize) / 2).coerceAtLeast(0.dp) }
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        StateGlyph(tone = tone, tint = accent, modifier = Modifier.padding(top = glyphTop))
        Spacer(Modifier.width(6.dp))
        Text(
            text =
                buildAnnotatedString {
                    withStyle(SpanStyle(color = accent)) { append(words.words) }
                    words.detail?.let { detail ->
                        withStyle(
                            SpanStyle(
                                color = inkSoft,
                                fontWeight = FontWeight.Normal,
                                fontSize = detailStyle.fontSize,
                                letterSpacing = detailStyle.letterSpacing,
                            ),
                        ) { append(if (words.words.isEmpty()) detail else " · $detail") }
                    }
                },
            style = wordStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The state chip (DESIGN 3.1), at most one per screen: a pill on the family container with the glyph in the accent
 * and the words in the on-container colour. TalkBack reads "Status: Armed". [icon] replaces the family glyph where
 * DESIGN 2.2 asks for another: check_circle on S5's "Ready to snipe", because a passed check is not a win.
 */
@Composable
fun StateChip(
    tone: StatusTone,
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = tone.icon,
) {
    val colors = KestrelTheme.status[tone]
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(colors.container, effects, label = "chip container")
    val accent by animateColorAsState(colors.accent, effects, label = "chip accent")
    val onContainer by animateColorAsState(colors.onContainer, effects, label = "chip words")
    Row(
        modifier =
            modifier
                .clearAndSetSemantics { contentDescription = "Status: $text" }
                .defaultMinSize(minHeight = 32.dp)
                .clip(CircleShape)
                .background(container)
                .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StateGlyph(tone = tone, tint = accent, icon = icon)
        Text(text = text, style = KestrelTheme.type.stateWord, color = onContainer, maxLines = 1)
    }
}

/** The family glyph at 18 dp; it crossfades when the state changes family. */
@Composable
private fun StateGlyph(
    tone: StatusTone,
    tint: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = tone.icon,
) {
    Crossfade(
        targetState = icon,
        modifier = modifier,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "state glyph",
    ) { icon ->
        SymbolIcon(icon, tint = tint, size = GlyphSize)
    }
}

/** State glyphs are 18 dp in labels and chips (DESIGN 2.11). */
private val GlyphSize = 18.dp
