package app.kestrel.ui.screens.result

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.kestrel.ui.components.BottomFade
import app.kestrel.ui.components.KestrelMenu
import app.kestrel.ui.components.LedgerRow
import app.kestrel.ui.components.LotPlate
import app.kestrel.ui.components.MoneyBlock
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.components.SectionHead
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.components.StateLabel
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.components.Timeline
import app.kestrel.ui.format.noWidow
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.PhotoStyle
import app.kestrel.ui.screens.lot.InkButton
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.PlateShapes
import app.kestrel.ui.theme.Spacing
import app.kestrel.ui.theme.StatusTone
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * S4, a snipe's result and receipt (DESIGN S4, hero moment two): close, share and overflow; the plate (with the WON
 * seal stamping once); the status line with day, time and zone; the headline and title; the card of this kind of
 * result; eBay's reply as a pull-quote; and the receipt, newest first. [plateKey] joins the plate to the list's in the
 * screen transition; null when S4 replaces S3 in place.
 */
@Composable
fun ResultScreen(
    state: ResultUiState,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    plateKey: String? = "plate-${state.id}",
    onClose: () -> Unit = {},
    onShare: () -> Unit = {},
    onOpenOnEbay: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAction: (LotAction) -> Unit = {},
    onSettle: (won: Boolean) -> Unit = {},
) {
    val won = state.variant == ResultVariant.Won
    // "You won" one step above every other result (rule MF7).
    val headlineStyle = if (won) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall
    // A Surface, so icons and text default to ink on the page in both themes.
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                ResultTopBar(state.canDelete, state.canSettle, onClose, onShare, onOpenOnEbay, onDelete, onSettle)
                Column(
                    Modifier
                        .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom))
                        .padding(start = Spacing.ColumnA, end = Spacing.ColumnA, bottom = Spacing.ScreenEnd),
                ) {
                    ResultPlate(state, plateKey, Modifier.zIndex(1f))
                    Spacer(Modifier.height(16.dp))
                    // The seal hangs only 12 dp below the plate, so the line under it has the whole width.
                    StateLabel(tone = state.tone, words = state.statusLine, large = true)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.headline,
                        style = headlineStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.title,
                        style = KestrelTheme.type.lotTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    state.paid?.let { PaidCard(it) }
                    state.sold?.let { SoldCard(it) }
                    if (state.secondChance) {
                        Spacer(Modifier.height(12.dp))
                        SecondChanceCard()
                    }
                    state.outcome?.let { OutcomeCard(it, onAction) }
                    state.quote?.let { quote ->
                        Spacer(Modifier.height(20.dp))
                        PullQuote(quote)
                    }
                    Spacer(Modifier.height(24.dp))
                    SectionHead("Receipt", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Timeline(state.receipt)
                }
            }
            BottomFade(windowInsets = windowInsets)
        }
    }
}

/** How far up a scene's focus moves on the 136 dp plate (DESIGN 2.4: BiasAlignment from -1 to 1). */
private const val SHORT_PLATE_FOCUS_LIFT = 0.35f

@Composable
private fun ResultTopBar(
    canDelete: Boolean,
    canSettle: Boolean,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onOpenOnEbay: () -> Unit,
    onDelete: () -> Unit,
    onSettle: (won: Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { SymbolIcon(Symbols.Close, contentDescription = "Close") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShare) { SymbolIcon(Symbols.Share, contentDescription = "Share receipt") }
        Box {
            IconButton(onClick = { expanded = true }) {
                SymbolIcon(Symbols.MoreVert, contentDescription = "More options")
            }
            KestrelMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(LotAction.OPEN_ON_EBAY.label) },
                    leadingIcon = { SymbolIcon(Symbols.OpenInNew) },
                    onClick = {
                        expanded = false
                        onOpenOnEbay()
                    },
                )
                if (canSettle) {
                    // A result Kestrel could not read: the user looked on eBay and says how it ended.
                    DropdownMenuItem(
                        text = { Text("Mark as won") },
                        leadingIcon = { SymbolIcon(Symbols.Trophy) },
                        onClick = {
                            expanded = false
                            onSettle(true)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Mark as lost") },
                        leadingIcon = { SymbolIcon(Symbols.Flag) },
                        onClick = {
                            expanded = false
                            onSettle(false)
                        },
                    )
                }
                if (canDelete) {
                    // The destructive item comes last, after a divider; its snackbar offers Undo (DESIGN Q22).
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { SymbolIcon(Symbols.Delete) },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** The plate: 176 dp when won (with the seal), 136 dp otherwise (DESIGN 2.4). */
@Composable
private fun ResultPlate(
    state: ResultUiState,
    plateKey: String?,
    modifier: Modifier = Modifier,
) {
    val won = state.variant == ResultVariant.Won
    // The shorter plate crops more of a scene's height, so its focus moves up to keep the top of the item.
    val photo =
        if (!won && state.photo.style == PhotoStyle.Scene) {
            state.photo.copy(focusY = (state.photo.focusY - SHORT_PLATE_FOCUS_LIFT).coerceIn(-1f, 1f))
        } else {
            state.photo
        }
    Box(modifier.fillMaxWidth()) {
        LotPlate(
            photo = photo,
            modifier = Modifier.fillMaxWidth().height(if (won) 176.dp else 136.dp),
            shape = PlateShapes.Result,
            cutoutInset = 18.dp,
            sharedElementKey = plateKey,
        )
        if (won) {
            WonSeal(
                stamp = state.stampSeal,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-14).dp, y = SealDrop),
            )
        }
    }
}

/**
 * The WON seal (DESIGN 3.15): a Cookie12Sided in the Won accent, rotated -10°, with the trophy and "WON". It stamps
 * once on the first open (scale 1.15 to 1, rotation -16° to -10°, fading in); with reduced motion it simply sits there.
 */
@Composable
private fun WonSeal(
    stamp: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val animate = stamp && !KestrelTheme.reduceMotion
    val settle = remember { Animatable(if (animate) 0f else 1f) }
    val fade = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) {
            coroutineScope {
                launch { fade.animateTo(1f, motion.fastEffectsSpec()) }
                settle.animateTo(1f, motion.defaultSpatialSpec())
            }
        }
    }
    val fill = KestrelTheme.status.won.accent
    val content = if (KestrelTheme.isDark) MaterialTheme.colorScheme.background else KestrelTheme.catalogue.card
    Box(
        modifier
            .size(SealSize)
            .graphicsLayer {
                val progress = settle.value
                val scale = 1.15f + (1f - 1.15f) * progress
                scaleX = scale
                scaleY = scale
                rotationZ = -16f + 6f * progress
                alpha = fade.value
            }.clip(MaterialShapes.Cookie12Sided.toShape())
            .background(fill)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SymbolIcon(Symbols.Trophy, tint = content, size = 26.dp)
            Spacer(Modifier.height(2.dp))
            SmallCaps("Won", color = content, style = KestrelTheme.type.sectionHead)
        }
    }
}

private val SealSize = 96.dp

/**
 * How far the seal hangs below the plate: enough to break its edge like a stamp, less than the 16 dp to the status
 * line, so the line never has to stop short of it.
 */
private val SealDrop = 12.dp

/** The won card: what it was won for against the max, the ledger to the total, and how eBay takes payment. */
@Composable
private fun PaidCard(paid: PaidCardText) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    ResultCard(Modifier.clearAndSetSemantics { contentDescription = "${paid.spoken} ${paid.note}" }) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).alignBy(LastBaseline)) {
                SmallCaps("Won for")
                AutoSizePrice(paid.wonFor)
            }
            if (paid.under != null || paid.maxLine != null) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.alignBy(LastBaseline)) {
                    paid.under?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KestrelTheme.status.won.accent,
                            maxLines = 1,
                        )
                    }
                    paid.maxLine?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = inkSoft, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = KestrelTheme.catalogue.rule)
        Spacer(Modifier.height(12.dp))
        paid.ledger.forEach { LedgerRow(it.label, it.value, Modifier.fillMaxWidth()) }
        paid.total?.let { LedgerRow(it.label, it.value, Modifier.fillMaxWidth(), total = true) }
        Spacer(Modifier.height(12.dp))
        GlyphText(Symbols.Info, 18.dp, paid.note, MaterialTheme.typography.bodySmall)
    }
}

/** A serif price on one line that steps down rather than clip at large text. */
@Composable
private fun AutoSizePrice(text: String) {
    val style = KestrelTheme.type.priceHero
    BasicText(
        text = text,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = style.fontSize),
    )
}

/** The lost card: "SOLD FOR" against "YOUR MAX", a hairline, then why it was lost. */
@Composable
private fun SoldCard(sold: SoldCardText) {
    ResultCard {
        MoneyBlock(sold.figures)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = KestrelTheme.catalogue.rule)
        Spacer(Modifier.height(12.dp))
        GlyphTitled(Symbols.Info, sold.reason, sold.explanation)
    }
}

/** If the winner does not pay, a Second Chance Offer may come: where to accept it, and what to ignore. */
@Composable
private fun SecondChanceCard() {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = container) {
        Box(Modifier.padding(16.dp)) {
            GlyphTitled(Symbols.Mail, ResultStateBuilder.SECOND_CHANCE_TITLE, ResultStateBuilder.SECOND_CHANCE)
        }
    }
}

/** Ended early, cancelled, unknown (on the card colour) and the two red failures (on the Failed container). */
@Composable
private fun OutcomeCard(
    outcome: OutcomeCardText,
    onAction: (LotAction) -> Unit,
) {
    val failed = outcome.tone == StatusTone.Failed
    val colors = KestrelTheme.status[outcome.tone]
    val container = if (failed) colors.container else KestrelTheme.catalogue.card
    val textColour = if (failed) colors.onContainer else MaterialTheme.colorScheme.onSurface
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = container, contentColor = textColour) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(outcome.text.noWidow(), style = MaterialTheme.typography.bodyLarge, color = textColour)
            outcome.action?.let { action ->
                when {
                    failed -> {
                        PrimaryButton(action.label, onClick = { onAction(action) })
                    }

                    // A tonal 40 dp button (DESIGN 3.17 "Tonal"): undoing is not the page's primary action.
                    action == LotAction.UNDO_CANCEL -> {
                        FilledTonalButton(
                            onClick = { onAction(action) },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = ButtonDefaults.contentPaddingFor(Spacing.InlineButtonHeight),
                            modifier = Modifier.heightIn(min = Spacing.InlineButtonHeight),
                        ) {
                            Text(action.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    else -> {
                        InkButton(action.label, onClick = { onAction(action) })
                    }
                }
            }
        }
    }
}

/** eBay's reply (DESIGN 3.14): a 3 dp ink bar, the timing line, then the quote in serif. */
@Composable
private fun PullQuote(quote: PullQuoteText) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clearAndSetSemantics { contentDescription = quote.spoken },
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.onSurface),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
            Text(quote.timing, style = MaterialTheme.typography.labelMedium, color = inkSoft)
            Spacer(Modifier.height(4.dp))
            Text(
                quote.quote.noWidow(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ResultCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = KestrelTheme.catalogue.card) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** A glyph centred on the first line, then the text. */
@Composable
private fun GlyphText(
    icon: Int,
    size: Dp,
    text: String,
    style: androidx.compose.ui.text.TextStyle,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    val iconTop = with(LocalDensity.current) { ((style.lineHeight.toDp() - size) / 2).coerceAtLeast(0.dp) }
    Row(verticalAlignment = Alignment.Top) {
        SymbolIcon(icon, tint = inkSoft, size = size, modifier = Modifier.padding(top = iconTop))
        Spacer(Modifier.width(8.dp))
        Text(text, style = style, color = inkSoft)
    }
}

/** A 20 dp glyph beside a title in titleMedium and a body in ink soft. */
@Composable
private fun GlyphTitled(
    icon: Int,
    title: String,
    body: String,
) {
    val titleStyle = MaterialTheme.typography.titleMedium
    val iconTop = with(LocalDensity.current) { ((titleStyle.lineHeight.toDp() - 20.dp) / 2).coerceAtLeast(0.dp) }
    Row(verticalAlignment = Alignment.Top) {
        val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
        SymbolIcon(icon, tint = inkSoft, size = 20.dp, modifier = Modifier.padding(top = iconTop))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
