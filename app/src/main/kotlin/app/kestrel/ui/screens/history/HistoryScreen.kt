package app.kestrel.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.BottomFade
import app.kestrel.ui.components.SectionHead
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.plural
import app.kestrel.ui.screens.home.HomeActions
import app.kestrel.ui.screens.home.LotRow
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing

/**
 * Every finished snipe (DESIGN History): back, "History", the totals card, then month sections of finished rows,
 * newest first. A tap opens the result. Stateless; the screen ends above the gesture handle with the bottom fade.
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onOpenResult: (Long) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val layoutDirection = LocalLayoutDirection.current
    val insets = windowInsets.asPaddingValues()
    val start = insets.calculateStartPadding(layoutDirection)
    val end = insets.calculateEndPadding(layoutDirection)
    val bottom = insets.calculateBottomPadding()
    val rowActions = remember(onOpenResult) { HomeActions(onOpenResult = onOpenResult) }
    val page = MaterialTheme.colorScheme.background
    Surface(modifier.fillMaxSize(), color = page) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(page)
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                IconButton(onClick = onBack) { SymbolIcon(Symbols.ArrowBack, contentDescription = "Back") }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = start + Spacing.ColumnA,
                            end = end + Spacing.ColumnA,
                            bottom = bottom + Spacing.ScreenEnd,
                        ),
                ) {
                    item(key = "title") {
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    state.summary?.let { summary ->
                        item(key = "summary") { SummaryCard(summary, Modifier.padding(top = Spacing.Medium)) }
                    }
                    if (state.isEmpty) item(key = "empty") { EmptyHistory(Modifier.padding(top = Spacing.Section)) }
                    state.months.forEachIndexed { monthIndex, month ->
                        item(key = "month-${month.title}") {
                            SectionHead(
                                title = month.title,
                                count = plural(month.lots.size, "lot", "lots"),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            top =
                                                if (monthIndex == 0 &&
                                                    state.summary == null
                                                ) {
                                                    Spacing.Medium
                                                } else {
                                                    Spacing.Section
                                                },
                                            bottom = Spacing.Small,
                                        ).heightIn(min = 20.dp),
                            )
                        }
                        month.lots.forEachIndexed { index, lot ->
                            item(key = "lot-${lot.id}") {
                                LotRow(
                                    lot = lot,
                                    now = state.now,
                                    index = index,
                                    count = month.lots.size,
                                    actions = rowActions,
                                    modifier =
                                        Modifier.padding(
                                            bottom =
                                                if (index <
                                                    month.lots.lastIndex
                                                ) {
                                                    ListItemDefaults.SegmentedGap
                                                } else {
                                                    0.dp
                                                },
                                        ),
                                )
                            }
                        }
                    }
                }
                BottomFade(windowInsets = windowInsets)
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + Spacing.Small),
                )
            }
        }
    }
}

/** "Won 1 of 2 in November" over "£11.00 under your max in total", on a card. */
@Composable
private fun SummaryCard(
    summary: HistorySummary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(KestrelTheme.catalogue.card, MaterialTheme.shapes.large)
            .padding(Spacing.CardInset),
    ) {
        Text(summary.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        summary.detail?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** History before anything has finished (DESIGN 3.19). */
@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = "No finished snipes yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.Small))
        Text(
            text = "When an auction ends, its result and receipt appear here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
