package app.kestrel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kestrel.ui.theme.KestrelTheme

/** The max field's input rules (DESIGN 3.18): digits and one ".", at most two decimals; "£" and "," are dropped. */
object MaxInput {
    /** Keeps what a max may contain: "£1,234.567" becomes "1234.56". */
    fun filter(text: String): String {
        val builder = StringBuilder()
        var seenPoint = false
        var decimals = 0
        for (character in text) {
            when {
                character.isDigit() && seenPoint && decimals < 2 -> {
                    builder.append(character)
                    decimals++
                }

                character.isDigit() && !seenPoint -> {
                    builder.append(character)
                }

                character == '.' && !seenPoint -> {
                    builder.append(character)
                    seenPoint = true
                }
            }
        }
        return builder.toString()
    }

    /** On blur the value shows two decimals: "90" becomes "90.00", "90.3" becomes "90.30". Empty stays empty. */
    fun formatOnBlur(text: String): String {
        if (text.isEmpty()) return text
        val whole = text.substringBefore('.').ifEmpty { "0" }
        val fraction = text.substringAfter('.', missingDelimiterValue = "").padEnd(2, '0')
        return "$whole.$fraction"
    }

    /** The field's filter as an input transformation. */
    val Transformation: InputTransformation =
        object : InputTransformation {
            override fun TextFieldBuffer.transformInput() {
                val current = asCharSequence().toString()
                val filtered = filter(current)
                if (filtered != current) replace(0, length, filtered)
            }
        }
}

/**
 * The max field (DESIGN 3.18): "YOUR MAX BID", then "£" in ink soft, the amount in priceHero with a 2 dp ink cursor,
 * and "At least £66.00" on the last baseline; an underline 2 dp ink when focused, 1 dp outline otherwise, 2 dp in the
 * Attention accent with the [error] line ("Too low: eBay needs at least £66.00") when the value is too low.
 *
 * [state] is hoisted into the screen state and keyed by the lot id, so it is never rebuilt on recomposition; "Use
 * £90.37" writes into it with setTextAndPlaceCursorAtEnd. [readOnly] is for the moment the edits lock.
 */
@Composable
fun MaxField(
    state: TextFieldState,
    minimum: String,
    modifier: Modifier = Modifier,
    currencySymbol: String = "£",
    error: String? = null,
    readOnly: Boolean = false,
    onDone: () -> Unit = {},
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    val attention = KestrelTheme.status.attention.accent
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (wasFocused && !focused) state.setTextAndPlaceCursorAtEnd(MaxInput.formatOnBlur(state.text.toString()))
        wasFocused = focused
    }
    val value = state.text.toString()
    Column(modifier) {
        SmallCaps("Your max bid")
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = currencySymbol,
                style = KestrelTheme.type.priceHero,
                color = inkSoft,
                modifier = Modifier.alignBy(LastBaseline),
            )
            Spacer(Modifier.width(2.dp))
            BasicTextField(
                state = state,
                modifier =
                    Modifier
                        .weight(1f)
                        .alignBy(LastBaseline)
                        .semantics {
                            contentDescription = "Your max bid"
                            stateDescription = value.ifEmpty { "Empty" }
                            if (error != null) error(error)
                        },
                readOnly = readOnly,
                inputTransformation = MaxInput.Transformation,
                textStyle = KestrelTheme.type.priceHero.copy(color = ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                onKeyboardAction = { onDone() },
                lineLimits = TextFieldLineLimits.SingleLine,
                interactionSource = interactions,
                cursorBrush = SolidColor(ink),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "At least $minimum",
                style = MaterialTheme.typography.bodyMedium,
                color = inkSoft,
                modifier = Modifier.alignBy(LastBaseline),
            )
        }
        val underlineColour =
            when {
                error != null -> attention
                focused -> ink
                else -> MaterialTheme.colorScheme.outline
            }
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(if (error != null || focused) 2.dp else 1.dp)
                .background(underlineColour),
        )
        if (error != null) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbols.Warning, tint = attention, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(text = error, style = MaterialTheme.typography.bodyMedium, color = attention)
            }
        }
    }
}
