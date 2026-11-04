package app.kestrel.ui.screens.home

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.OfflineBanner
import app.kestrel.ui.components.SectionHead
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.clock
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.plural
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing
import java.time.Instant

/**
 * What S1 can ask for. The destination turns each into navigation, a ViewModel call or a system page.
 *
 * - [onRaiseMax]: the leading half of "Raise max" (the raise sheet); [onQuickRaise]: the menu's "Raise to £2,200.00,
 *   the next bid", at once; [onStopSniping]: the menu's "Stop sniping".
 * - [onLotAction]: a row's other buttons ("Set a max", "Sign in", "Open eBay page", "Fix", "Open on eBay").
 * - [onReadinessAction]: the pill's Fix, Review or Sign in; [onOpenReady]: the pill itself.
 */
@Immutable
class HomeActions(
    val onOpenLot: (Long) -> Unit = {},
    val onOpenResult: (Long) -> Unit = {},
    val onOpenGroup: (Long) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenReady: () -> Unit = {},
    val onReadinessAction: (PillAction) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRaiseMax: (Long) -> Unit = {},
    val onQuickRaise: (Long) -> Unit = {},
    val onStopSniping: (Long) -> Unit = {},
    val onLotAction: (Long, LotAction) -> Unit = { _, _ -> },
    val onSeeAllFinished: () -> Unit = {},
)

/**
 * S1, the list of snipes (DESIGN S1): the dated header, the offline banner, the buckets with the hero and the rows,
 * "See all finished snipes", and the dock with the readiness pill and Add. Stateless: everything comes from [state]
 * and goes out through [actions]. Pull to refresh asks for a check of the lots not checked in the last five minutes.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    actions: HomeActions = HomeActions(),
) {
    val layoutDirection = LocalLayoutDirection.current
    val insets = windowInsets.asPaddingValues()
    val top = insets.calculateTopPadding()
    val start = insets.calculateStartPadding(layoutDirection)
    val end = insets.calculateEndPadding(layoutDirection)
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = actions.onRefresh,
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullIndicator(
                            refreshing = state.refreshing,
                            state = pullState,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
                        )
                    },
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                start = start + Spacing.ColumnA,
                                end = end + Spacing.ColumnA,
                                top = top,
                                bottom = Spacing.AboveDock,
                            ),
                    ) {
                        homeItems(state, actions)
                    }
                }
                StatusBarFade(top)
                // The snackbar's own 12 dp margin plus 4 dp puts its edges on the cards' 16 dp column.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = start + 4.dp, end = end + 4.dp, bottom = Spacing.Small),
                )
            }
            HomeDock(
                pill = state.pill,
                showAdd = state.loaded && !state.isEmpty,
                windowInsets = windowInsets,
                onOpenReady = actions.onOpenReady,
                onPillAction = actions.onReadinessAction,
                onAdd = actions.onAdd,
            )
        }
    }
}

private fun LazyListScope.homeItems(
    state: HomeUiState,
    actions: HomeActions,
) {
    item(key = "header") {
        HomeHeader(state, actions.onOpenSettings, moving())
    }
    state.offline?.let { notice ->
        item(key = "offline") {
            OfflineBanner(
                title = offlineTitle(notice, state.now),
                detail = offlineDetail(notice, state.now),
                modifier = moving().padding(top = Spacing.Small),
            )
        }
    }
    if (state.isEmpty) {
        item(key = "empty") {
            EmptyCatalogue(
                leadSeconds = state.defaultLeadSeconds,
                onPasteLink = actions.onAdd,
                modifier = moving().padding(top = Spacing.Section),
            )
        }
    }
    state.sections.forEachIndexed { sectionIndex, section ->
        item(key = "head-${section.bucket.name}") {
            SectionHead(
                title = section.bucket.title,
                count = plural(section.lotCount, "lot", "lots"),
                modifier =
                    moving()
                        .fillMaxWidth()
                        .padding(
                            top =
                                if (sectionIndex ==
                                    0
                                ) {
                                    Spacing.Medium
                                } else {
                                    Spacing.Section
                                },
                            bottom = Spacing.Small,
                        ).heightIn(min = SectionHeadHeight),
            )
        }
        section.blocks.forEachIndexed { blockIndex, block ->
            blockItems(block, blockGap = if (blockIndex > 0) Spacing.BlockGap else 0.dp, state, actions)
        }
    }
    if (state.showFinishedLink) {
        item(key = "finished") {
            // The words line up with column A; the button's own padding reaches into the margin.
            TextButton(
                onClick = actions.onSeeAllFinished,
                modifier = moving().padding(top = Spacing.Small).offset(x = -TextButtonInset),
            ) {
                Text("See all finished snipes", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The horizontal padding inside a text button, before its words. */
private val TextButtonInset = 12.dp

/** A section head is 20 dp tall (DESIGN S1). */
private val SectionHeadHeight = 20.dp

private fun LazyListScope.blockItems(
    block: HomeBlock,
    blockGap: Dp,
    state: HomeUiState,
    actions: HomeActions,
) {
    val count = block.segmentCount
    block.group?.let { group ->
        item(key = "group-${group.id}") {
            GroupHeaderRow(
                group = group,
                count = count,
                onOpen = { actions.onOpenGroup(group.id) },
                modifier = moving().segment(index = 0, count = count, blockGap = blockGap),
            )
        }
    }
    val offset = if (block.group != null) 1 else 0
    block.lots.forEachIndexed { lotIndex, lot ->
        val index = lotIndex + offset
        item(key = "lot-${lot.id}") {
            val segment = moving().segment(index, count, blockGap)
            if (block.opensWithHero && lotIndex == 0) {
                HeroCard(lot, state.now, index, count, actions, segment)
            } else {
                LotRow(lot, state.now, index, count, actions, segment)
            }
        }
    }
}

/**
 * How list items move (DESIGN 2.10): fade in on defaultEffectsSpec, move on defaultSpatialSpec, fade out on
 * fastEffectsSpec. With reduced motion they cut.
 */
@Immutable
private class ItemMotion(
    val fadeIn: FiniteAnimationSpec<Float>?,
    val placement: FiniteAnimationSpec<IntOffset>?,
    val fadeOut: FiniteAnimationSpec<Float>?,
)

@Composable
private fun itemMotion(): ItemMotion {
    if (KestrelTheme.reduceMotion) return ItemMotion(null, null, null)
    val motion = MaterialTheme.motionScheme
    return ItemMotion(motion.defaultEffectsSpec(), motion.defaultSpatialSpec(), motion.fastEffectsSpec())
}

/** This item's [animateItem] with Kestrel's motion. */
@Composable
private fun LazyItemScope.moving(): Modifier {
    val motion = itemMotion()
    return Modifier.animateItem(motion.fadeIn, motion.placement, motion.fadeOut)
}

/** Places a segment in its block: the block gap above the first, the 2 dp segment gap below all but the last. */
private fun Modifier.segment(
    index: Int,
    count: Int,
    blockGap: Dp,
): Modifier =
    padding(
        top = if (index == 0) blockGap else 0.dp,
        bottom = if (index < count - 1) ListItemDefaults.SegmentedGap else 0.dp,
    )

/**
 * The header (DESIGN S1, 70 dp): the date and zone in small caps over "Snipes", and the account button with its
 * right edge on column A.
 */
@Composable
private fun HomeHeader(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SmallCaps(HomeText.overline(state.now), maxLines = 2)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Snipes",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        AccountButton(state.accountName, onOpenSettings)
    }
}

/** A 48 dp circle in the card colour with a 1 dp rule and the person glyph; it opens Settings. */
@Composable
private fun AccountButton(
    accountName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = HomeText.accountDescription(accountName)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = KestrelTheme.catalogue.card,
        border = BorderStroke(1.dp, KestrelTheme.catalogue.rule),
        modifier = modifier.size(Spacing.TouchTarget).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            SymbolIcon(Symbols.Person, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** The pull-to-refresh indicator: Material's LoadingIndicator, or a still hourglass with reduced motion. */
@Composable
private fun PullIndicator(
    refreshing: Boolean,
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
) {
    if (KestrelTheme.reduceMotion) {
        PullToRefreshDefaults.IndicatorBox(state = state, isRefreshing = refreshing, modifier = modifier) {
            SymbolIcon(Symbols.HourglassTop, tint = MaterialTheme.colorScheme.primary, size = 20.dp)
        }
    } else {
        PullToRefreshDefaults.LoadingIndicator(state = state, isRefreshing = refreshing, modifier = modifier)
    }
}

/** Page colour behind the status bar, fading out below it, so scrolled lots pass under the clock quietly. */
@Composable
private fun BoxScope.StatusBarFade(statusBar: Dp) {
    if (statusBar == 0.dp) return
    val page = MaterialTheme.colorScheme.background
    val fade = statusBar + 8.dp
    val solid = statusBar / fade
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(fade)
            .background(Brush.verticalGradient(0f to page, solid to page, 1f to page.copy(alpha = 0f))),
    )
}

private fun offlineTitle(
    notice: OfflineNotice,
    now: Instant,
): String = notice.since?.let { "Offline since ${moment(now, it)}" } ?: "Offline"

private fun offlineDetail(
    notice: OfflineNotice,
    now: Instant,
): String =
    notice.since?.let { "Prices are from ${moment(now, it)}. Kestrel can’t bid until the phone reconnects." }
        ?: "Kestrel can’t bid until the phone reconnects."

private fun moment(
    now: Instant,
    instant: Instant,
): String = if (isSameDay(now, instant)) instant.clock() else "${instant.dayDate()} ${instant.clock()}"
