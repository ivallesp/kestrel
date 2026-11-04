package app.kestrel.ui.screens.add

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.ConnectedToggleRow
import app.kestrel.ui.components.KestrelDialog
import app.kestrel.ui.components.LotPlate
import app.kestrel.ui.components.MaxField
import app.kestrel.ui.components.OddPenceWords
import app.kestrel.ui.components.SumRow
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.components.appendFigure
import app.kestrel.ui.format.NARROW_NO_BREAK_SPACE
import app.kestrel.ui.format.NO_BREAK_SPACE
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.PlateShapes
import app.kestrel.ui.theme.Spacing
import app.kestrel.ui.components.OddPenceCard as SharedOddPenceCard

/** The S2 lot header (DESIGN S2 item 2): the 104 dp plate, the title in headlineSmall and the seller. */
@Composable
internal fun LotHeader(
    header: ListingHeader,
    modifier: Modifier = Modifier,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LotPlate(header.photo, Modifier.size(104.dp), shape = PlateShapes.Row, cutoutInset = 12.dp)
        Spacer(Modifier.width(Spacing.PlateToText))
        Column(Modifier.weight(1f)) {
            Text(
                text = header.title.forDisplay(),
                style = MaterialTheme.typography.headlineSmall.copy(lineBreak = LineBreak.Heading),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            header.sellerName?.let { seller ->
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Seller $seller" },
                ) {
                    SymbolIcon(Symbols.Storefront, tint = inkSoft, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = seller,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (header.privateSeller) {
                Text(text = "Private seller", style = MaterialTheme.typography.bodySmall, color = inkSoft)
            }
        }
    }
}

/**
 * The max field of S2 and the sheets ([MaxField], DESIGN 3.18). A selected amount (the raise sheet's prefill) is
 * tinted lightly, so the figures stay as readable as the rest of the field.
 */
@Composable
internal fun MaxEntryField(
    entry: MaxEntry,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.primary
    val selection =
        remember(ink) { TextSelectionColors(handleColor = ink, backgroundColor = ink.copy(alpha = SELECTION_ALPHA)) }
    CompositionLocalProvider(LocalTextSelectionColors provides selection) {
        MaxField(
            state = entry.field,
            minimum = entry.minimum,
            currencySymbol = entry.currencySymbol,
            error = entry.error,
            readOnly = entry.readOnly,
            onDone = onDone,
            modifier = modifier,
        )
    }
}

private const val SELECTION_ALPHA = 0.18f

/** The sum row, or the sentence that stands in for it when max and postage cannot be added up. */
@Composable
internal fun WorstCaseRow(
    worstCase: WorstCase,
    modifier: Modifier = Modifier,
) {
    when (worstCase) {
        is WorstCase.Sum -> {
            SumRow(max = worstCase.max, postage = worstCase.postage, total = worstCase.total, modifier = modifier)
        }

        is WorstCase.Sentence -> {
            Text(
                text = worstCase.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier,
            )
        }
    }
}

/** The odd-pence card of S2 and the sheets (DESIGN S2 item 6), drawn by the shared [SharedOddPenceCard]. */
@Composable
internal fun OddPenceCard(
    oddPence: OddPence,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (oddPence) {
        is OddPence.Suggestion -> {
            SharedOddPenceCard(
                title = OddPenceWords.suggestionTitle(oddPence.amount),
                detail = OddPenceWords.suggestionDetail(oddPence.roundAmount),
                useDescription = "Use ${oddPence.amount.formatted()}",
                onUse = onUse,
                modifier = modifier,
            )
        }

        is OddPence.Tip -> {
            SharedOddPenceCard(
                title = OddPenceWords.tipTitle(oddPence.currency),
                detail = OddPenceWords.tipDetail(oddPence.currency),
                useDescription = null,
                onUse = onUse,
                modifier = modifier,
            )
        }
    }
}

/** "Bid timing", the lead in words, the connected toggles and the hint (DESIGN S2 item 7). */
@Composable
internal fun BidTimingSection(
    timing: BidTimingChoice,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = "Bid timing",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                text = "${secondsText(timing.selectedSeconds.toLong())} before the end",
                style = MaterialTheme.typography.bodyMedium,
                color = inkSoft,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(8.dp))
        ConnectedToggleRow(
            options = timing.options.map { secondsText(it.toLong()) },
            selectedIndex = timing.selectedIndex,
            onSelect = onSelect,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (timing.extendedBidding) EXTENDED_BIDDING_HINT else LEAD_HINT,
            style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading),
            color = inkSoft,
        )
    }
}

/** "6 s suits most connections. Choose 10 s on a weak signal." */
internal val LEAD_HINT =
    "${secondsText(6)} suits most connections. " +
        "Choose ${secondsText(10)} on a${NO_BREAK_SPACE}weak${NO_BREAK_SPACE}signal."

/** The S2 hint on a listing with extended bidding (DESIGN S2 item 7, MF33). */
internal val EXTENDED_BIDDING_HINT =
    "This listing uses extended bidding: any bid in the last 60${NARROW_NO_BREAK_SPACE}s, Kestrel’s too, resets " +
        "the clock to 60${NARROW_NO_BREAK_SPACE}s."

/**
 * A neutral tonal button (DESIGN 3.17 "Tonal"): secondaryContainer with ink words. [height] is 48 dp for page
 * actions and 56 dp where it takes the place of the primary button.
 */
@Composable
internal fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    height: Dp = Spacing.TouchTarget,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = icon != null),
        modifier = modifier.heightIn(min = height),
    ) {
        icon?.let {
            SymbolIcon(it, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A text button in ink, 48 dp tall (DESIGN 3.17 "Text"): "Paste a different link", "Cancel". */
@Composable
internal fun InkTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.heightIn(min = Spacing.TouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A filled ink button that is not full width, for the one action on a notice (DESIGN 3.17 "Primary", inline). */
@Composable
internal fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(Spacing.TouchTarget),
        modifier = modifier.heightIn(min = Spacing.TouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A notice on the page: a glyph in ink soft, the sentence in titleMedium and an optional detail. Neutral, because
 * a listing that cannot be sniped is not a state of a lot.
 */
@Composable
internal fun NoticeCard(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    content: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = KestrelTheme.catalogue.card,
    ) {
        Row(Modifier.padding(Spacing.CardInset), verticalAlignment = Alignment.Top) {
            SymbolIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 1.dp),
                )
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            }
        }
    }
}

/**
 * [text] with [figure] set in tabular figures when [tabular] (rule T4: ticking or aligned figures only), and the
 * words either side joined to it with no-break spaces, so "at 20:04:12 GMT" never breaks apart.
 */
internal fun figureLine(
    text: String,
    figure: String?,
    tabular: Boolean = true,
): AnnotatedString =
    buildAnnotatedString {
        val at = figure?.let { text.indexOf(it) } ?: -1
        if (figure == null || at < 0) {
            append(text)
            return@buildAnnotatedString
        }
        val before = text.substring(0, at)
        val after = text.substring(at + figure.length)
        append(if (before.endsWith(' ')) before.dropLast(1) + NO_BREAK_SPACE else before)
        if (tabular) appendFigure(figure) else append(figure)
        append(if (after.startsWith(' ')) NO_BREAK_SPACE + after.drop(1) else after)
    }

/** A radio row's semantics: one choice of several, read as a radio button. */
internal fun Modifier.radioRole(): Modifier = semantics { role = Role.RadioButton }

/** The max spelt out before arming a max far above the price, in Kestrel's one dialog style. */
@Composable
internal fun WordsCheckDialog(
    check: WordsCheck,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KestrelDialog(
        onDismissRequest = onDismiss,
        confirmButton = { InkTextButton(check.confirmLabel, onClick = onConfirm) },
        dismissButton = { InkTextButton("Change max", onClick = onDismiss) },
        title = { Text("Check your max", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    text = check.amount,
                    style = KestrelTheme.type.priceLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = check.words,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = check.reason,
                    style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
