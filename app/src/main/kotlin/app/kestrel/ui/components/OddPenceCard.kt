package app.kestrel.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kestrel.model.Money
import app.kestrel.ui.format.NO_BREAK_SPACE
import app.kestrel.ui.format.formatted

/** The words of the odd-pence card in the listing's own small unit: pence for pounds, cents for the rest. */
object OddPenceWords {
    /** "pence" for pounds, "cents" for dollars, euros and the other currencies eBay lists in. */
    fun unit(currency: String): String = if (currency == "GBP") "pence" else "cents"

    /** "Make it £90.37". */
    fun suggestionTitle(amount: Money): String = "Make it ${amount.formatted()}"

    /** "Odd pence beat bidders who stop at £90.00." (rule MF35: never "tie", never a written-in amount). */
    fun suggestionDetail(roundAmount: Money): String =
        "Odd ${unit(roundAmount.currency)} beat bidders who stop at$NO_BREAK_SPACE${roundAmount.formatted()}."

    /** "Try odd pence", while no max is typed. */
    fun tipTitle(currency: String): String = "Try odd ${unit(currency)}"

    /** "Odd pence beat bidders who stop at round numbers." */
    fun tipDetail(currency: String): String =
        "Odd ${unit(currency)} beat bidders who stop at round${NO_BREAK_SPACE}numbers."
}

/**
 * The odd-pence card (DESIGN S2 item 6), shared by S2 and every edit sheet: neutral surfaceContainerHigh (rule MF17),
 * the suggestion or the tip, and an outlined "Use" (40 dp, 1 dp outline, ink words) when there is an amount to use.
 * A tonal button would be the same paper as the card, so the outline is what makes "Use" read as a button.
 * [useDescription] is what TalkBack reads for "Use" ("Use £90.37"); null hides the button.
 */
@Composable
fun OddPenceCard(
    title: String,
    detail: String,
    useDescription: String?,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (useDescription != null) {
                Spacer(Modifier.width(12.dp))
                OutlinedActionButton(
                    text = "Use",
                    onClick = onUse,
                    modifier = Modifier.semantics { contentDescription = useDescription },
                )
            }
        }
    }
}
