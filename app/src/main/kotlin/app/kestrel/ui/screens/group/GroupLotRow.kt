package app.kestrel.ui.screens.group

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kestrel.model.Money
import app.kestrel.ui.components.LotPlate
import app.kestrel.ui.components.OutlinedActionButton
import app.kestrel.ui.components.StateLabel
import app.kestrel.ui.components.StateTonalButton
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.components.appendFigure
import app.kestrel.ui.format.NARROW_NO_BREAK_SPACE
import app.kestrel.ui.format.absoluteTime
import app.kestrel.ui.format.clock
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.plural
import app.kestrel.ui.format.spoken
import app.kestrel.ui.format.timeLeft
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.action
import app.kestrel.ui.model.label
import app.kestrel.ui.model.spokenLabel
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.PlateShapes
import app.kestrel.ui.theme.Spacing
import app.kestrel.ui.theme.StatusTone
import java.time.Duration
import java.time.Instant

/** The actions a lot row offers (DESIGN 3.5); "Edit max" and the receipts live on the lot's own screens. */
private val RowActions =
    setOf(
        LotAction.SET_MAX,
        LotAction.RAISE_MAX,
        LotAction.SIGN_IN,
        LotAction.OPEN_EBAY_PAGE,
        LotAction.FIX,
        LotAction.OPEN_ON_EBAY,
    )

/**
 * The action a row shows at [now], if any. Finished rows show none, except "Undo cancel" on a snipe the group
 * cancelled while its auction still runs (DESIGN Group "After a win").
 */
fun GroupLotRowState.rowAction(now: Instant): LotAction? =
    status.action(now)?.takeIf {
        (it in RowActions && !status.kind.isFinal) || it == LotAction.UNDO_CANCEL
    }

/**
 * A lot row (DESIGN 3.5) as the Group screen lists its members, without the group flag: the 72 dp plate, the state
 * label, the title, the price with bids and postage, when it ends, the flags and the one action. One merged node for
 * TalkBack, with the action as a custom action.
 */
@Composable
fun GroupLotRow(
    row: GroupLotRowState,
    now: Instant,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onAction: (LotAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = row.rowAction(now)
    val description = describe(row, now)
    SegmentedListItem(
        onClick = onOpen,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = description
                if (action != null) {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(action.label) {
                                onAction(action)
                                true
                            },
                        )
                }
            },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            LotPlate(row.photo, Modifier.size(72.dp), shape = PlateShapes.Row, cutoutInset = 8.dp)
            Spacer(Modifier.width(Spacing.PlateToText))
            Column(Modifier.weight(1f)) {
                StateLabel(tone = row.status.tone, words = row.status.label(now))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = row.title.forDisplay(),
                    style = KestrelTheme.type.lotTitleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                PriceLines(row)
                if (row.endsAt.isAfter(now)) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text =
                            buildAnnotatedString {
                                append("${absoluteTime(now, row.endsAt)} · in ")
                                appendFigure(timeLeft(now, row.endsAt))
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Flags(row)
                action?.let { RowActionButton(it, onClick = { onAction(it) }, Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

/** The price and its meta; "Won for" and "Sold for" lines on ended lots. */
@Composable
private fun PriceLines(row: GroupLotRowState) {
    Column {
        val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
        val body = MaterialTheme.typography.bodyMedium
        when (val status = row.status) {
            is LotStatus.Won -> {
                val wonFor = status.wonFor ?: row.price
                FlowRow {
                    Text("Won for ", style = body, color = inkSoft, modifier = Modifier.alignByBaseline())
                    wonFor?.let { Price(it, Modifier.alignByBaseline()) }
                    row.postage?.let {
                        Text(
                            " + ${it.formatted()} postage",
                            style = body,
                            color = inkSoft,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                wonAgainstMax(wonFor, status.max)?.let { Text(it, style = body, color = inkSoft) }
            }

            is LotStatus.Lost -> {
                val soldFor = status.soldFor ?: row.price
                FlowRow {
                    Text("Sold for ", style = body, color = inkSoft, modifier = Modifier.alignByBaseline())
                    soldFor?.let { Price(it, Modifier.alignByBaseline()) }
                }
                status.max?.let { Text("Your max was ${it.formatted()}", style = body, color = inkSoft) }
            }

            else -> {
                FlowRow {
                    row.price?.let { Price(it, Modifier.alignByBaseline()) }
                    Spacer(Modifier.width(8.dp))
                    Text(meta(row), style = body, color = inkSoft, modifier = Modifier.alignByBaseline())
                }
            }
        }
    }
}

@Composable
private fun Price(
    amount: Money,
    modifier: Modifier = Modifier,
) {
    Text(
        amount.formatted(),
        style = KestrelTheme.type.priceRow,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** "5 bids · £3.49 postage". */
private fun meta(row: GroupLotRowState): String {
    val bids = row.bidCount?.let { plural(it, "bid", "bids") }
    val postage = row.postage?.let { "${it.formatted()} postage" } ?: "postage not listed"
    return listOfNotNull(bids, postage).joinToString(" · ")
}

/** "£11.00 under your £45.00 max", or "At your £45.00 max". */
private fun wonAgainstMax(
    wonFor: Money?,
    max: Money?,
): String? {
    if (wonFor == null || max == null || wonFor.currency != max.currency) return null
    return if (max >
        wonFor
    ) {
        "${(max - wonFor).formatted()} under your ${max.formatted()} max"
    } else {
        "At your ${max.formatted()} max"
    }
}

/** The flags (DESIGN 3.5 item 5), each on its own: extended bidding and a failed check. */
@Composable
private fun Flags(row: GroupLotRowState) {
    if (row.status.kind.isFinal) return
    Column {
        if (row.extendedBidding) Flag(Symbols.Timer, EXTENDED_FLAG)
        row.failedCheckAt?.let { Flag(Symbols.Sync, "Couldn’t check at ${it.clock()} · retrying") }
    }
}

@Composable
private fun Flag(
    @DrawableRes icon: Int,
    text: String,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodySmall
    // The icon sits on the middle of the first line.
    val iconTop = with(LocalDensity.current) { ((style.lineHeight.toDp() - FlagIconSize) / 2).coerceAtLeast(0.dp) }
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Top) {
        SymbolIcon(icon, tint = inkSoft, size = FlagIconSize, modifier = Modifier.padding(top = iconTop))
        Spacer(Modifier.width(4.dp))
        Text(text, style = style, color = inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The row action (DESIGN 3.5 item 6): outlined "Set a max", or the Attention-tonal fix for the state. */
@Composable
private fun RowActionButton(
    action: LotAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (action == LotAction.SET_MAX) {
        OutlinedActionButton(text = action.label, onClick = onClick, modifier = modifier)
    } else if (action == LotAction.UNDO_CANCEL) {
        // Neutral tonal (DESIGN 3.17): a cancelled lot is not a problem to fix.
        FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(Spacing.InlineButtonHeight),
            modifier = modifier.heightIn(min = Spacing.InlineButtonHeight),
        ) {
            Text(action.label, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        StateTonalButton(
            tone = StatusTone.Attention,
            text = action.label,
            onClick = onClick,
            modifier = modifier,
            icon = if (action == LotAction.RAISE_MAX) Symbols.ArrowUpward else null,
        )
    }
}

/** One sentence for TalkBack: title, state, money, end. */
private fun describe(
    row: GroupLotRowState,
    now: Instant,
): String =
    buildString {
        append(row.title)
        append(". ")
        append(row.status.spokenLabel(now))
        append(". ")
        if (!row.status.kind.isFinal) {
            row.price?.let { append("Current bid ${it.formatted()}") }
            row.bidCount?.let { append(", ${plural(it, "bid", "bids")}") }
            row.postage?.let { append(", postage ${it.formatted()}") }
            append(". ")
        }
        if (row.endsAt.isAfter(now)) {
            append("Ends ${absoluteTime(now, row.endsAt)}, in ${Duration.between(now, row.endsAt).spoken()}.")
        }
    }.trim()

private val FlagIconSize = 16.dp

/** The extended-bidding flag (DESIGN 3.5, MF33). */
internal val EXTENDED_FLAG = "Extended bidding: Kestrel’s bid will reset the clock to 60${NARROW_NO_BREAK_SPACE}s."
