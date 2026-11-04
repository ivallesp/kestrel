package app.kestrel.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.kestrel.model.Money
import app.kestrel.ui.components.CalmLoadingIndicator
import app.kestrel.ui.components.Figure
import app.kestrel.ui.components.LotPlate
import app.kestrel.ui.components.MoneyBlock
import app.kestrel.ui.components.MoneyFigures
import app.kestrel.ui.components.OutlinedActionButton
import app.kestrel.ui.components.RollingDigits
import app.kestrel.ui.components.StateChip
import app.kestrel.ui.components.StateLabel
import app.kestrel.ui.components.StateTonalButton
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.components.lotMoneyFigures
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.format.formatted
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.chipText
import app.kestrel.ui.model.label
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.PlateShapes
import app.kestrel.ui.theme.Spacing
import app.kestrel.ui.theme.StatusTone
import java.time.Instant

// The segments of S1 and History: the hero (DESIGN 3.4), the lot row (3.5), the group header (3.6) and the
// Raise max split button (3.17).

private const val OPEN_SNIPE = "Open snipe"

/** The plate size of rows (DESIGN 2.4). */
private val RowPlateSize = 72.dp

/** Above an inline button whose 48 dp touch target already adds 4 dp around its 40 dp face. */
private val AboveInlineButton = Spacing.Small - (Spacing.TouchTarget - Spacing.InlineButtonHeight) / 2

/**
 * The hero ("next up"): the plate with the state chip, the title, the money block, the time block and, for lots that
 * need the user, their action. It is the first segment of its block; a tap opens S3 with the plate travelling.
 */
@Composable
internal fun HeroCard(
    lot: HomeLot,
    now: Instant,
    index: Int,
    count: Int,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val open = { actions.onOpenLot(lot.id) }
    val customActions = rowCustomActions(lot, actions)
    SegmentedListItem(
        onClick = open,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            modifier.semantics {
                onClick(label = OPEN_SNIPE) {
                    open()
                    true
                }
                if (customActions.isNotEmpty()) this.customActions = customActions
            },
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding = PaddingValues(0.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(HeroPlateHeight)) {
                LotPlate(
                    photo = lot.photo,
                    modifier = Modifier.fillMaxSize(),
                    shape = PlateShapes.Hero,
                    sharedElementKey = "plate-${lot.id}",
                )
                StateChip(lot.status.tone, lot.status.chipText(), Modifier.padding(12.dp))
            }
            Column(
                Modifier.padding(
                    start = Spacing.CardInset,
                    end = Spacing.CardInset,
                    top = 12.dp,
                    bottom = if (lot.action != null) 12.dp else Spacing.CardInset,
                ),
            ) {
                Text(
                    text = lot.snipe.title.forDisplay(),
                    style = KestrelTheme.type.lotTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                MoneyBlock(heroMoney(lot))
                Spacer(Modifier.height(12.dp))
                HeroTimeBlock(lot, now)
                lot.action?.let { action ->
                    Spacer(Modifier.height(AboveInlineButton + 4.dp))
                    LotActionButton(lot, action, actions)
                }
            }
        }
    }
}

private val HeroPlateHeight = 128.dp

/** The hero's money: the price against the max with the headroom rule, or the plain grid before a max. */
private fun heroMoney(lot: HomeLot): MoneyFigures {
    val snipe = lot.snipe
    val price = snipe.lastPrice
    val max = snipe.maxBid
    return when {
        price != null && max != null -> {
            lotMoneyFigures(price, snipe.lastBidCount ?: 0, max, snipe.postage, lot.status.tone)
        }

        max != null -> {
            MoneyFigures(
                startLabel = "Your max",
                startValue = max.formatted(),
                endLabel = "Postage",
                endValue = snipe.postage?.formatted() ?: "",
                spoken = "Your max ${max.formatted()}. ${HomeText.postageWords(snipe.postage)}.",
            )
        }

        else -> {
            MoneyFigures(
                startLabel = "Current",
                startValue = price?.formatted() ?: "",
                endLabel = "Postage",
                endValue = snipe.postage?.formatted() ?: "",
                spoken = "Current bid ${price?.formatted()}. ${HomeText.postageWords(snipe.postage)}.",
            )
        }
    }
}

/**
 * The hero's time block (DESIGN 3.3): "Ends in 13 m 07 s" over when Kestrel bids, against the end clock over
 * "Today · GMT". Only the changed digits of the countdown roll. One sentence for TalkBack, not a live region.
 */
@Composable
private fun HeroTimeBlock(
    lot: HomeLot,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val end = Instant.ofEpochMilli(lot.snipe.endTimeUtcMillis)
    val ink = MaterialTheme.colorScheme.onSurface
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    val lineOne = MaterialTheme.typography.titleMedium
    val spoken = HomeText.heroSpokenTime(lot, now)
    Row(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        SymbolIcon(Symbols.Timer, tint = ink, size = 22.dp, modifier = Modifier.align(Alignment.CenterVertically))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alignBy(FirstBaseline)) {
            val countdown = HomeText.heroCountdown(now, end)
            if (countdown == null) {
                Text("Ended", style = lineOne, color = ink)
            } else {
                Row {
                    Text("Ends in ", style = lineOne, color = ink, modifier = Modifier.alignByBaseline())
                    RollingDigits(countdown, style = lineOne, color = ink, modifier = Modifier.alignByBaseline())
                }
            }
            Row {
                if (lot.status is LotStatus.BidSent || lot.status is LotStatus.ReadingResult) {
                    CalmLoadingIndicator(size = 20.dp, modifier = Modifier.padding(end = 6.dp))
                }
                Text(HomeText.heroLine(lot, now), style = MaterialTheme.typography.bodyMedium, color = inkSoft)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.alignBy(FirstBaseline), horizontalAlignment = Alignment.End) {
            Figure(HomeText.heroClock(now, end), style = lineOne, color = ink)
            Text(HomeText.heroDayAndZone(now, end), style = MaterialTheme.typography.bodyMedium, color = inkSoft)
        }
    }
}

/**
 * A lot row (DESIGN 3.5): the 72 dp plate, then the state label, the title, the price line, the time line, the
 * flags and the action. Finished rows show what it was won or sold for instead, and no action. One merged sentence
 * for TalkBack, with the row's actions as custom actions.
 */
@Composable
internal fun LotRow(
    lot: HomeLot,
    now: Instant,
    index: Int,
    count: Int,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val finished = lot.status.kind.isFinal
    val open = { if (finished) actions.onOpenResult(lot.id) else actions.onOpenLot(lot.id) }
    val spoken = HomeText.spokenRow(lot, now)
    val customActions = rowCustomActions(lot, actions)
    SegmentedListItem(
        onClick = open,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            modifier.semantics {
                contentDescription = spoken
                onClick(label = if (finished) LotAction.VIEW_RECEIPT.label else OPEN_SNIPE) {
                    open()
                    true
                }
                if (customActions.isNotEmpty()) this.customActions = customActions
            },
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding =
            PaddingValues(
                start = Spacing.CardInset,
                end = Spacing.CardInset,
                top = 12.dp,
                bottom =
                    if (lot.action !=
                        null
                    ) {
                        12.dp - (Spacing.TouchTarget - Spacing.InlineButtonHeight) / 2
                    } else {
                        12.dp
                    },
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Row(Modifier.fillMaxWidth()) {
            LotPlate(lot.photo, Modifier.size(RowPlateSize), sharedElementKey = "plate-${lot.id}")
            Spacer(Modifier.width(Spacing.PlateToText))
            Column(Modifier.weight(1f)) {
                // The words are read from the row's own sentence, so the parts stay quiet.
                Column(Modifier.clearAndSetSemantics {}) {
                    StateLabel(lot.status.tone, lot.status.label(now))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = lot.snipe.title.forDisplay(),
                        style = KestrelTheme.type.lotTitleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (finished) FinishedLines(lot, now) else OpenLines(lot, now)
                }
                lot.action?.let { action ->
                    Spacer(Modifier.height(AboveInlineButton))
                    LotActionButton(lot, action, actions)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.OpenLines(
    lot: HomeLot,
    now: Instant,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    BaselineFlow(horizontalGap = 8.dp) {
        HomeText.price(lot)?.let { RowPrice(it) }
        Text(HomeText.priceMeta(lot, now), style = MaterialTheme.typography.bodyMedium, color = inkSoft)
    }
    Spacer(Modifier.height(2.dp))
    Text(
        text = HomeText.timeLine(lot, now),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    HomeText.flags(lot, now).forEach { flag ->
        Spacer(Modifier.height(2.dp))
        Row {
            SymbolIcon(flag.icon, tint = inkSoft, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(flag.text, style = MaterialTheme.typography.bodySmall, color = inkSoft)
        }
    }
}

@Composable
private fun ColumnScope.FinishedLines(
    lot: HomeLot,
    now: Instant,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    val body = MaterialTheme.typography.bodyMedium
    val price = HomeText.endedPrice(lot)
    if (price != null) {
        BaselineFlow(horizontalGap = 6.dp) {
            Text(price.label, style = body, color = inkSoft)
            Text(price.amount, style = KestrelTheme.type.priceRow, color = MaterialTheme.colorScheme.onSurface)
            price.after?.let { Text(it, style = body, color = inkSoft) }
        }
    }
    HomeText.endedNote(lot, now)?.let { note ->
        if (price != null) Spacer(Modifier.height(2.dp))
        // Balanced lines, so a wrapped note never leaves one word on its own ("max").
        Text(note, style = body.copy(lineBreak = LineBreak.Heading), color = inkSoft)
    }
}

/** The row price; a new price slides up 8 dp and fades in while the old one fades out (DESIGN 2.10). */
@Composable
private fun RowPrice(price: String) {
    val reduceMotion = KestrelTheme.reduceMotion
    val motion = MaterialTheme.motionScheme
    val slide = motion.defaultSpatialSpec<IntOffset>()
    val fadeIn = motion.defaultEffectsSpec<Float>()
    val fadeOut = motion.fastEffectsSpec<Float>()
    val rise = with(LocalDensity.current) { 8.dp.roundToPx() }
    AnimatedContent(
        targetState = price,
        transitionSpec = {
            if (reduceMotion) {
                ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
            } else {
                (slideInVertically(slide) { rise } + fadeIn(fadeIn)) togetherWith fadeOut(fadeOut)
            }
        },
        label = "row price",
    ) { shown ->
        Text(shown, style = KestrelTheme.type.priceRow, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** The row's button, 40 dp tall inside a 48 dp touch target. */
@Composable
private fun LotActionButton(
    lot: HomeLot,
    action: LotAction,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    when (action) {
        LotAction.RAISE_MAX -> {
            RaiseMaxButton(lot, actions, modifier)
        }

        LotAction.SET_MAX -> {
            OutlinedActionButton(action.label, onClick = { actions.onLotAction(lot.id, action) }, modifier = modifier)
        }

        else -> {
            StateTonalButton(
                tone = StatusTone.Attention,
                text = action.label,
                onClick = { actions.onLotAction(lot.id, action) },
                modifier = modifier,
            )
        }
    }
}

/**
 * "Raise max" as a split button in the Attention tonal colours (DESIGN 3.17): the leading half opens the raise
 * sheet; the trailing half opens the menu with the next bid at once, a divider, and "Stop sniping". The chevron turns
 * while the menu is open.
 */
@Composable
private fun RaiseMaxButton(
    lot: HomeLot,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable(lot.id) { mutableStateOf(false) }
    val attention = KestrelTheme.status.attention
    val colors =
        ButtonDefaults.filledTonalButtonColors(
            containerColor = attention.container,
            contentColor = attention.onContainer,
        )
    val height = Spacing.InlineButtonHeight
    val rotation by animateFloatAsState(
        targetValue = if (menuOpen) 180f else 0f,
        animationSpec = if (KestrelTheme.reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "menu chevron",
    )
    val minimum = (lot.status as? LotStatus.OverMax)?.minimumBid
    Box(modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(
                    onClick = { actions.onRaiseMax(lot.id) },
                    colors = colors,
                    contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
                    modifier = Modifier.heightIn(min = height),
                ) {
                    SymbolIcon(Symbols.ArrowUpward, size = SplitButtonDefaults.leadingButtonIconSizeFor(height))
                    Spacer(Modifier.width(6.dp))
                    Text(LotAction.RAISE_MAX.label, style = MaterialTheme.typography.labelLarge)
                }
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it },
                    colors = colors,
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
                    modifier = Modifier.heightIn(min = height),
                ) {
                    SymbolIcon(
                        Symbols.ExpandMore,
                        contentDescription = "More for this snipe",
                        size = SplitButtonDefaults.trailingButtonIconSizeFor(height),
                        modifier = Modifier.graphicsLayer { rotationZ = rotation },
                    )
                }
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            RaiseMaxMenuItems(
                minimum = minimum,
                onQuickRaise = {
                    menuOpen = false
                    actions.onQuickRaise(lot.id)
                },
                onStopSniping = {
                    menuOpen = false
                    actions.onStopSniping(lot.id)
                },
            )
        }
    }
}

/**
 * The Raise max menu (DESIGN 3.17): "Raise to £2,200.00, the next bid" when eBay's next bid is known, a divider, and
 * "Stop sniping". Stop sniping is the destructive one, so it is last and its snackbar offers Undo.
 */
@Composable
internal fun ColumnScope.RaiseMaxMenuItems(
    minimum: Money?,
    onQuickRaise: () -> Unit,
    onStopSniping: () -> Unit,
) {
    if (minimum != null) {
        DropdownMenuItem(
            text = { Text("Raise to ${minimum.formatted()}, the next bid") },
            leadingIcon = { SymbolIcon(Symbols.ArrowUpward, size = 20.dp) },
            onClick = onQuickRaise,
        )
        HorizontalDivider()
    }
    DropdownMenuItem(
        text = { Text("Stop sniping") },
        leadingIcon = { SymbolIcon(Symbols.DoNotDisturbOn, size = 20.dp) },
        onClick = onStopSniping,
    )
}

/** The row's actions for TalkBack: "Raise max" and "Stop sniping" over the max, or the one action it shows. */
private fun rowCustomActions(
    lot: HomeLot,
    actions: HomeActions,
): List<CustomAccessibilityAction> =
    when (val action = lot.action) {
        null -> {
            emptyList()
        }

        LotAction.RAISE_MAX -> {
            listOf(
                CustomAccessibilityAction(LotAction.RAISE_MAX.label) {
                    actions.onRaiseMax(lot.id)
                    true
                },
                CustomAccessibilityAction("Stop sniping") {
                    actions.onStopSniping(lot.id)
                    true
                },
            )
        }

        else -> {
            listOf(
                CustomAccessibilityAction(action.label) {
                    actions.onLotAction(lot.id, action)
                    true
                },
            )
        }
    }

/**
 * The first segment of a group's block (DESIGN 3.6): link, "Portable audio: win 1 of 2", what Kestrel does once one
 * is won, and a chevron. Neutral colours, because a group is not a state. A tap opens the Group screen.
 */
@Composable
internal fun GroupHeaderRow(
    group: GroupHeader,
    count: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onOpen,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
        modifier =
            modifier.semantics {
                onClick(label = "Open group") {
                    onOpen()
                    true
                }
            },
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
            ),
        contentPadding = PaddingValues(horizontal = Spacing.CardInset, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SymbolIcon(Symbols.Link, tint = scheme.onSecondaryContainer, size = 20.dp)
            Spacer(Modifier.width(Spacing.Medium))
            Column(Modifier.weight(1f)) {
                Text(
                    HomeText.groupTitle(group),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSecondaryContainer,
                )
                Text(
                    HomeText.groupDetail(group),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(Spacing.Medium))
            SymbolIcon(Symbols.ChevronRight, tint = scheme.onSecondaryContainer, size = 20.dp)
        }
    }
}

/**
 * Lays its children left to right on one shared first baseline, wrapping to a new line (with its own baseline)
 * when the next child does not fit: the price and its meta, "Won for £34.00 + £3.10 postage". A line of digits gets
 * a shorter box than a line of words, so aligning by top would set the figures high (DESIGN Q11).
 */
@Composable
internal fun BaselineFlow(
    horizontalGap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val gap = horizontalGap.roundToPx()
        // Lines of indices that fit the width.
        val lines = mutableListOf<MutableList<Int>>()
        var lineWidth = 0
        placeables.forEachIndexed { index, placeable ->
            val current = lines.lastOrNull()
            val needed =
                if (current == null ||
                    current.isEmpty()
                ) {
                    placeable.width
                } else {
                    lineWidth + gap + placeable.width
                }
            if (current == null || needed > constraints.maxWidth) {
                lines += mutableListOf(index)
                lineWidth = placeable.width
            } else {
                current += index
                lineWidth = needed
            }
        }
        val baselines =
            placeables.map { placeable ->
                placeable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified }
                    ?: placeable.height
            }
        val lineTops = mutableListOf<Int>()
        val lineBaselines = mutableListOf<Int>()
        var top = 0
        lines.forEach { line ->
            val baseline = line.maxOf { baselines[it] }
            val below = line.maxOf { placeables[it].height - baselines[it] }
            lineTops += top
            lineBaselines += baseline
            top += baseline + below
        }
        val width = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: lineWidth
        layout(width.coerceAtLeast(constraints.minWidth), top.coerceAtLeast(constraints.minHeight)) {
            lines.forEachIndexed { lineIndex, line ->
                var x = 0
                line.forEach { index ->
                    val placeable = placeables[index]
                    placeable.placeRelative(x, lineTops[lineIndex] + lineBaselines[lineIndex] - baselines[index])
                    x += placeable.width + gap
                }
            }
        }
    }
}
