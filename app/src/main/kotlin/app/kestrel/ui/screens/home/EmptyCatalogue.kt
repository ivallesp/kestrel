package app.kestrel.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.secondsText

/**
 * S1 with nothing on the list (DESIGN 3.19): a dashed plate waiting for the first lot, what Kestrel does, three steps
 * and "Paste eBay link". [leadSeconds] is the lead new snipes get, for the third step.
 */
@Composable
internal fun EmptyCatalogue(
    leadSeconds: Int,
    onPasteLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        EmptyPlate()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Nothing on the list yet",
            style = MaterialTheme.typography.headlineMedium,
            color = scheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Kestrel bids for you in the last seconds of an eBay auction, up to the maximum you set.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            steps(leadSeconds).forEachIndexed { index, (title, detail) ->
                Step(number = index + 1, title = title, detail = detail)
            }
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Paste eBay link", onClick = onPasteLink, icon = Symbols.ContentPaste)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Kestrel never bids above your max.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The steps of DESIGN 6.3; the third names the lead time Kestrel will really use. */
private fun steps(leadSeconds: Int): List<Pair<String, String>> =
    listOf(
        "Share a listing" to "In the eBay app or your browser, tap Share, then Kestrel.",
        "Set your maximum" to "You see the most you could pay, with postage.",
        "Kestrel bids at the end" to "From this phone, ${secondsText(leadSeconds.toLong())} before the auction closes.",
    )

/** The dashed plate: 160 dp, 20 dp corners, a 1.5 dp outline dashed 6 on 4 off, and the share badge. */
@Composable
private fun EmptyPlate(modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        style =
                            Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                            ),
                    )
                }.padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                SymbolIcon(Symbols.Share, tint = scheme.onSecondaryContainer, size = 24.dp)
            }
            Spacer(Modifier.height(12.dp))
            SmallCaps("Your first lot appears here")
        }
    }
}

@Composable
private fun Step(
    number: Int,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.displaySmall.copy(lineHeight = 44.sp),
            color = scheme.onBackground,
            modifier = Modifier.width(40.dp).clearAndSetSemantics {},
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onBackground)
            // Balanced lines, so a two-line step never ends on one word.
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
