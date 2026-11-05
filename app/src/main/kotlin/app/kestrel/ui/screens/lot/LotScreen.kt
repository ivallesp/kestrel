package app.kestrel.ui.screens.lot

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.BottomFade
import app.kestrel.ui.components.KestrelMenu
import app.kestrel.ui.components.LotPlate
import app.kestrel.ui.components.MoneyBlock
import app.kestrel.ui.components.OfflineBanner
import app.kestrel.ui.components.SectionHead
import app.kestrel.ui.components.StateChip
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.components.Timeline
import app.kestrel.ui.format.noWidow
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.PhotoStyle
import app.kestrel.ui.shell.StatusBarIconsEffect
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.SheetShape
import app.kestrel.ui.theme.Spacing

/** The photo band, status bar included (DESIGN S3 1). */
val PhotoBandHeight = 232.dp

/** The sheet rises over the bottom 28 dp of the band. */
private val SheetOverlap = 28.dp

/** The bar under the status bar once the sheet is scrolled up: 8 dp, the 48 dp buttons, 8 dp. */
private val TopBarHeight = 64.dp

/**
 * S3, one lot's detail and its final minute (DESIGN S3): the photograph under the status bar with back and overflow,
 * then a sheet with the state chip, the title, the countdown and times, the final-minute scale, the status card, the
 * money, the edit row, the checks that passed and the timeline. Stateless: [state] says everything, the callbacks do
 * everything.
 */
@Composable
fun LotScreen(
    state: LotUiState,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    scrollState: ScrollState = rememberScrollState(),
    onBack: () -> Unit = {},
    onAction: (LotAction) -> Unit = {},
    onOpenOnEbay: () -> Unit = {},
    onStopSniping: () -> Unit = {},
    onDelete: () -> Unit = {},
    onTestBidding: () -> Unit = {},
) {
    val page = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val statusInset = windowInsets.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val sheetTop = PhotoBandHeight - SheetOverlap
    // Once the sheet reaches the buttons, a page-coloured bar takes over, so nothing floats over money or time (P6).
    val barHeight = statusInset + TopBarHeight
    val collapsed by remember(barHeight, density) {
        derivedStateOf { scrollState.value >= with(density) { (sheetTop - barHeight).toPx() } }
    }
    // Light icons over a photograph and in dark theme (DESIGN Q23); dark ones over a light cutout plate, which has no
    // scrim, and the theme's own once the bar covers the band.
    StatusBarIconsEffect(lightIcons = lightStatusBarIcons(state.photo, KestrelTheme.isDark, collapsed))
    // A Surface, so anything that takes the content colour is ink on the page in both themes.
    Surface(modifier.fillMaxSize(), color = page) {
        Box(Modifier.fillMaxSize()) {
            PhotoBand(state, statusInset)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                Spacer(Modifier.height(sheetTop))
                LotSheet(state, windowInsets, barHeight, onAction)
            }
            if (collapsed) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .background(page),
                ) {
                    CollapsedBarWords(
                        state,
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = CollapsedWordsStart, end = CollapsedWordsStart, bottom = 8.dp)
                            .height(Spacing.TouchTarget),
                    )
                    HorizontalDivider(
                        Modifier.align(Alignment.BottomCenter),
                        thickness = 1.dp,
                        color = KestrelTheme.catalogue.rule,
                    )
                }
            }
            PhotoButtons(
                menu = state.menu,
                overPhoto = !collapsed,
                windowInsets = windowInsets,
                onBack = onBack,
                onOpenOnEbay = onOpenOnEbay,
                onStopSniping = onStopSniping,
                onDelete = onDelete,
                onTestBidding = onTestBidding,
            )
            BottomFade(windowInsets = windowInsets)
        }
    }
}

/**
 * Whether the status-bar icons are light: over a scene photograph (under its scrim) and in dark theme, but dark over
 * a light-theme cutout plate, whose paper would leave white icons near 2.6:1 (DESIGN Q23). Once the bar covers the
 * band they follow the theme.
 */
fun lightStatusBarIcons(
    photo: LotPhoto,
    darkTheme: Boolean,
    collapsed: Boolean = false,
): Boolean = darkTheme || (!collapsed && photo.style == PhotoStyle.Scene)

/** The words start after the back button: 8 dp in, the 48 dp button, 8 dp. */
private val CollapsedWordsStart = 64.dp

/**
 * Once the sheet is scrolled under the bar, the bar keeps what the scrolled-away header said: the lot's short title
 * and its countdown ("Canon AE-1" over "36 s left"). Not read separately: the sheet speaks for both.
 */
@Composable
private fun CollapsedBarWords(
    state: LotUiState,
    modifier: Modifier = Modifier,
) {
    val countdown = state.countdown
    val time = if (countdown.figure.isEmpty()) countdown.unit else "${countdown.figure} ${countdown.unit}"
    Column(modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.Center) {
        Text(
            text = state.shortTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = time,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The photograph: a scene fills the band (cropped on its focus); a studio cutout sits on the plate, clear of the
 * status bar and of the sheet. Over a scene, and over the darker dark-theme plate, a top gradient keeps the status
 * bar legible; a light cutout plate needs none, because the icons turn dark over it.
 */
@Composable
private fun PhotoBand(
    state: LotUiState,
    statusInset: Dp,
) {
    val key = "plate-${state.id}"
    Box(
        Modifier
            .fillMaxWidth()
            .height(PhotoBandHeight)
            .background(KestrelTheme.catalogue.plate),
    ) {
        if (state.photo.style == PhotoStyle.Scene) {
            LotPlate(state.photo, Modifier.fillMaxSize(), shape = RectangleShape, sharedElementKey = key)
        } else {
            LotPlate(
                state.photo,
                Modifier
                    .fillMaxSize()
                    .padding(top = statusInset + 8.dp, bottom = SheetOverlap + 12.dp, start = 64.dp, end = 64.dp),
                shape = RectangleShape,
                cutoutInset = 0.dp,
                sharedElementKey = key,
            )
        }
        if (lightStatusBarIcons(state.photo, KestrelTheme.isDark)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
            )
        }
    }
}

/** Back and overflow: 48 dp circles of black at 40 % with white icons, 8 dp in from the edges and the status bar. */
@Composable
private fun PhotoButtons(
    menu: LotMenuState,
    overPhoto: Boolean,
    windowInsets: WindowInsets,
    onBack: () -> Unit,
    onOpenOnEbay: () -> Unit,
    onStopSniping: () -> Unit,
    onDelete: () -> Unit,
    onTestBidding: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(8.dp),
    ) {
        PhotoButton(Symbols.ArrowBack, "Back", overPhoto, onBack)
        Spacer(Modifier.weight(1f))
        Box {
            PhotoButton(Symbols.MoreVert, "More options", overPhoto, onClick = { expanded = true })
            KestrelMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(LotAction.OPEN_ON_EBAY.label) },
                    leadingIcon = { SymbolIcon(Symbols.OpenInNew) },
                    onClick = {
                        expanded = false
                        onOpenOnEbay()
                    },
                )
                if (menu.testBidding) {
                    DropdownMenuItem(
                        text = { Text("Test bidding now") },
                        leadingIcon = { SymbolIcon(Symbols.TaskAlt) },
                        onClick = {
                            expanded = false
                            onTestBidding()
                        },
                    )
                }
                menu.stopSniping?.let { stop ->
                    DropdownMenuItem(
                        text = { Text(stop.label) },
                        leadingIcon = { SymbolIcon(Symbols.DoNotDisturbOn) },
                        enabled = stop.enabled,
                        onClick = {
                            expanded = false
                            onStopSniping()
                        },
                    )
                }
                if (menu.canDelete) {
                    // The destructive item comes last, after a divider; its snackbar offers Undo (DESIGN 3.17, Q22).
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

@Composable
private fun PhotoButton(
    icon: Int,
    description: String,
    overPhoto: Boolean,
    onClick: () -> Unit,
) {
    // Over the photograph: a 40 % black circle with a white icon (DESIGN 3.17); on the bar: an ink icon.
    val container = if (overPhoto) Color.Black.copy(alpha = 0.40f) else Color.Transparent
    val content = if (overPhoto) Color.White else MaterialTheme.colorScheme.onSurface
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(Spacing.TouchTarget),
        colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = content),
    ) {
        SymbolIcon(icon, contentDescription = description)
    }
}

/** The sheet over the photograph: page colour, 28 dp top corners, 16 dp sides, content to the inset plus 32 dp. */
@Composable
private fun LotSheet(
    state: LotUiState,
    windowInsets: WindowInsets,
    barHeight: Dp,
    onAction: (LotAction) -> Unit,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val barBottom = with(density) { barHeight.toPx() }
    val trackHeight = with(density) { FinalMinuteScaleTrackHeight.toPx() }
    // Where the scale's track ends on screen: once it has gone under the collapsed bar, its labels go with it.
    var scaleTrackBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background, SheetShape)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(start = Spacing.ColumnA, end = Spacing.ColumnA, top = 16.dp, bottom = Spacing.ScreenEnd),
    ) {
        StateChip(state.status.tone, state.chipText)
        state.offline?.let { offline ->
            Spacer(Modifier.height(12.dp))
            OfflineBanner(offline.title, offline.detail)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.title,
            style = KestrelTheme.type.lotTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        CountdownRow(state.countdown, state.times)
        state.scale?.let { scale ->
            Spacer(Modifier.height(20.dp))
            FinalMinuteScale(
                scale,
                Modifier
                    .onGloballyPositioned { scaleTrackBottom = it.positionInRoot().y + trackHeight }
                    .graphicsLayer { alpha = if (scaleTrackBottom <= barBottom) 0f else 1f },
            )
        }
        state.card?.let { card ->
            Spacer(Modifier.height(20.dp))
            StatusCardView(card, onAction)
        }
        state.note?.let { note ->
            Spacer(Modifier.height(16.dp))
            Text(note.noWidow(), style = MaterialTheme.typography.bodyMedium, color = inkSoft)
        }
        state.extendedNote?.let { note ->
            Spacer(Modifier.height(12.dp))
            Text(note.noWidow(), style = MaterialTheme.typography.bodySmall, color = inkSoft)
        }
        Spacer(Modifier.height(20.dp))
        MoneyBlock(state.money)
        state.edit?.let { edit ->
            Spacer(Modifier.height(16.dp))
            EditRow(edit, onAction)
        }
        if (state.checks.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            ChecksCard(state.checks)
        }
        Spacer(Modifier.height(24.dp))
        SectionHead("Timeline", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Timeline(state.timeline)
        // Spoken only at the moments of DESIGN S3: 30 and 10 seconds to the bid, bid sent, eBay's reply.
        Box(
            Modifier
                .size(1.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = state.announcement.orEmpty()
                },
        )
    }
}

/** The page of a snipe that is not on the list: deleted, or opened from an old notification. */
@Composable
fun NotOnTheList(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    showWords: Boolean = true,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(windowInsets)) {
            Box(Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) { SymbolIcon(Symbols.ArrowBack, contentDescription = "Back") }
            }
            if (showWords) {
                Column(Modifier.padding(horizontal = Spacing.ColumnA)) {
                    Text(
                        text = "Not on your list",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "This snipe was deleted. Finished snipes stay in History until you delete them.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
