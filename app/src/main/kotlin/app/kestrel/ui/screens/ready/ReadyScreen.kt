package app.kestrel.ui.screens.ready

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.BottomFade
import app.kestrel.ui.components.SectionHead
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.components.StateChip
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.clock
import app.kestrel.ui.format.clockFor
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.dayLabel
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.noWidow
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing
import app.kestrel.ui.theme.StatusTone
import java.time.Instant

/**
 * S5, Ready to snipe (DESIGN S5 and 3.9): every check the phone needs to bid on time, problems first, each with the
 * button that fixes it. "Check again" re-runs them; a fixed row moves into "All good" and the counts follow.
 */
@Composable
fun ReadyScreen(
    state: ReadyUiState,
    onBack: () -> Unit,
    onCheckAgain: () -> Unit,
    onAction: (CheckAction) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    val bottomInset = windowInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(windowInsets.only(TopAndSides))) {
                SetupTopBar(
                    navigationIcon = Symbols.ArrowBack,
                    navigationLabel = "Back",
                    onNavigate = onBack,
                    ruled = { listState.canScrollBackward },
                    actions = { CheckAgainButton(checking = state.checkInProgress, onClick = onCheckAgain) },
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = bottomInset + Spacing.ScreenEnd),
                ) {
                    item(key = "header") { Header(state) }
                    // While a check runs every row says "Checking…"; before the first one the rows are the checks
                    // themselves, so the page never goes blank (DESIGN S5).
                    val checking = state.checkInProgress
                    val rows = state.rows.ifEmpty { if (checking) CheckRows.pending() else emptyList() }
                    sectionsOf(rows, checkingFirstTime = state.rows.isEmpty()).forEach { section ->
                        item(key = "head-${section.standing}", contentType = "head") {
                            SectionHead(
                                title = section.title,
                                count = section.rows.size.toString(),
                                modifier =
                                    rowMotion()
                                        .padding(
                                            start = Spacing.ColumnA,
                                            end = Spacing.ColumnA,
                                            top = 24.dp,
                                            bottom = 8.dp,
                                        ),
                            )
                        }
                        itemsIndexed(
                            items = section.rows,
                            key = { _, row -> row.key },
                            contentType = { _, _ -> "row" },
                        ) { index, row ->
                            CheckRowItem(
                                row = row,
                                index = index,
                                count = section.rows.size,
                                checking = checking,
                                onAction = onAction,
                                modifier =
                                    rowMotion()
                                        .padding(horizontal = Spacing.ColumnA)
                                        .padding(top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap),
                            )
                        }
                    }
                    // The cadence footer follows the rows; before the first check there is nothing to follow.
                    if (state.rows.isNotEmpty()) item(key = "footer") { Footer() }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        ).windowInsetsPadding(windowInsets.only(BottomAndSides)),
            )
            BottomFade(windowInsets = windowInsets)
        }
    }
}

/** How often the checks run (DESIGN MF43, the re-check footer). */
@Composable
private fun Footer() {
    Text(
        text = CADENCE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.ColumnA, end = Spacing.ColumnA, top = 16.dp),
    )
}

/** "Check again" with refresh; "Checking…" and disabled while a check runs. No spinner (DESIGN S5). */
@Composable
private fun CheckAgainButton(
    checking: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = !checking) {
        SymbolIcon(Symbols.Refresh, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(if (checking) "Checking…" else "Check again", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Header(state: ReadyUiState) {
    val checkedAt = state.checkedAt
    val problems = state.problems.size
    Column(Modifier.padding(horizontal = Spacing.ColumnA)) {
        SmallCaps(if (checkedAt == null) "Checking now" else checkedOverline(state.now, checkedAt))
        // A neutral title: the chip under it gives the verdict, so the page never says ready while it is not.
        Text(
            text = "This phone",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        val deadline = state.nextBid?.takeIf { problems > 0 }?.let { deadlineSentence(problems, it, state.now) }
        if (checkedAt != null) {
            Spacer(Modifier.height(8.dp))
            if (problems == 0) {
                StateChip(StatusTone.Won, "Ready to snipe", icon = Symbols.CheckCircle)
            } else {
                StateChip(StatusTone.Attention, needsAttention(problems))
            }
            if (deadline != null) {
                Spacer(Modifier.height(12.dp))
                Text(deadline, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(if (deadline != null) 4.dp else 12.dp))
        Text(
            text = "Kestrel bids from this phone, so it has to be on, online and free to act at the exact second.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One check (DESIGN 3.9): a 40 dp glyph circle, the name, the state line and detail, and its button or tick. A button
 * sits at the end of the name's line, so the state line and the detail run the full width under both (3.9: three
 * lines at most, also with large text). While [checking], the state line reads "Checking…" in ink soft and the
 * verdict waits.
 */
@Composable
private fun CheckRowItem(
    row: CheckRow,
    index: Int,
    count: Int,
    checking: Boolean,
    onAction: (CheckAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val attention = KestrelTheme.status.attention
    val problem = row.standing == CheckStanding.Problem && !checking
    val button = row.action != null && !checking
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
        contentPadding = PaddingValues(16.dp),
        verticalAlignment = Alignment.Top,
        leadingContent = {
            GlyphCircle(
                icon = row.icon,
                container = if (problem) attention.container else scheme.surfaceContainerHigh,
                tint = if (problem) attention.accent else scheme.onSurfaceVariant,
            )
        },
        trailingContent = { if (!checking && !button) Trailing(row, onAction) },
        supportingContent = { Supporting(row, checking) },
    ) {
        if (button) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Trailing(row, onAction)
            }
        } else {
            Text(row.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
    }
}

@Composable
private fun Supporting(
    row: CheckRow,
    checking: Boolean,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (checking) {
            Text(CHECKING, style = MaterialTheme.typography.labelLarge, color = inkSoft)
            if (row.detail.isNotEmpty()) DetailText(row.detail, inkSoft)
            return@Column
        }
        when (row.standing) {
            CheckStanding.Problem -> {
                val accent = KestrelTheme.status.attention.accent
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SymbolIcon(Symbols.Warning, tint = accent, size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(row.stateWords.orEmpty(), style = KestrelTheme.type.stateWord, color = accent)
                }
            }

            CheckStanding.Optional -> {
                Text(row.stateWords.orEmpty(), style = MaterialTheme.typography.labelLarge, color = inkSoft)
            }

            CheckStanding.Passed, CheckStanding.NotYet -> {}
        }
        DetailText(row.detail, inkSoft)
    }
}

/**
 * A row's detail. A two-part detail ("0.04 s off Google time · synced 1 min ago") stays on one line when it fits;
 * otherwise its parts go on lines of their own without the dot, so no line ends on a separator. Body breaks on the
 * whole measure, with no lone word on the last line.
 */
@Composable
private fun DetailText(
    detail: String,
    color: Color,
) {
    val style = MaterialTheme.typography.bodyMedium
    val parts = detail.split(DETAIL_SEPARATOR)
    if (parts.size < 2) {
        Text(detail.noWidow(), style = style, color = color)
        return
    }
    Layout(
        content = {
            Text(detail, style = style, color = color, maxLines = 1, softWrap = false)
            parts.forEach { Text(it.noWidow(), style = style, color = color) }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val whole = measurables.first().measure(loose.copy(maxWidth = Constraints.Infinity))
        if (whole.width <= constraints.maxWidth) {
            layout(whole.width, whole.height) { whole.place(0, 0) }
        } else {
            val lines = measurables.drop(1).map { it.measure(loose) }
            layout(lines.maxOf { it.width }, lines.sumOf { it.height }) {
                var y = 0
                lines.forEach { line ->
                    line.place(0, y)
                    y += line.height
                }
            }
        }
    }
}

/** How the rows join the two parts of a detail: a no-break space keeps the dot with the words before it. */
private const val DETAIL_SEPARATOR = "\u00A0· "

private const val CHECKING = "Checking…"

@Composable
private fun Trailing(
    row: CheckRow,
    onAction: (CheckAction) -> Unit,
) {
    val action = row.action
    when {
        row.standing == CheckStanding.Problem && action != null -> {
            InlinePrimaryButton(action.label, onClick = { onAction(action) })
        }

        row.standing == CheckStanding.Optional && action != null -> {
            InlineTonalButton(action.label, onClick = { onAction(action) })
        }

        row.standing == CheckStanding.Passed -> {
            SymbolIcon(Symbols.CheckCircle, tint = KestrelTheme.status.won.accent, contentDescription = "Passed")
        }

        row.standing == CheckStanding.NotYet -> {
            SymbolIcon(Symbols.Info, tint = MaterialTheme.colorScheme.onSurfaceVariant, contentDescription = "Not yet")
        }
    }
}

/** Rows move between sections and fade in and out once (DESIGN 2.10); with reduced motion they cut. */
@Composable
private fun LazyItemScope.rowMotion(): Modifier {
    val motion = MaterialTheme.motionScheme
    return if (KestrelTheme.reduceMotion) {
        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        Modifier.animateItem(
            fadeInSpec = motion.defaultEffectsSpec(),
            placementSpec = motion.defaultSpatialSpec(),
            fadeOutSpec = motion.fastEffectsSpec(),
        )
    }
}

/** A section of rows that share a standing, with its head. */
private data class CheckSection(
    val standing: CheckStanding,
    val title: String,
    val rows: List<CheckRow>,
)

private fun sectionsOf(
    rows: List<CheckRow>,
    checkingFirstTime: Boolean,
): List<CheckSection> =
    listOf(
        CheckStanding.Problem to "Needs attention",
        CheckStanding.Optional to "Recommended",
        CheckStanding.Passed to "All good",
        CheckStanding.NotYet to if (checkingFirstTime) "Checks" else "Later",
    ).mapNotNull { (standing, title) ->
        rows.filter { it.standing == standing }.takeIf { it.isNotEmpty() }?.let { CheckSection(standing, title, it) }
    }

/** "Checked today 18:13" (set in small caps). */
private fun checkedOverline(
    now: Instant,
    checkedAt: Instant,
): String = "Checked ${dayLabel(now, checkedAt)} ${checkedAt.clock()}"

private fun needsAttention(problems: Int): String =
    if (problems == 1) "1 thing needs attention" else "$problems things need attention"

/** "Fix it before 18:21:34, when Kestrel bids on your Canon AE-1 Program." (DESIGN 6.2). */
internal fun deadlineSentence(
    problems: Int,
    nextBid: NextBid,
    now: Instant,
): String {
    val pronoun = if (problems == 1) "it" else "them"
    val time =
        if (isSameDay(now, nextBid.at)) {
            clockFor(now, nextBid.at)
        } else {
            "${nextBid.at.dayDate()} at ${nextBid.at.clock()}"
        }
    return "Fix $pronoun before $time, when Kestrel bids on your ${nextBid.title}."
}

/**
 * The cadence footer (DESIGN S5 item 10). The platform has no timed re-check: the checks run when this
 * page opens and when you come back to it, and the dry run before each bid.
 */
private const val CADENCE =
    "Kestrel runs these checks whenever you open this page, and does a dry run about 10 minutes before each bid."

private val TopAndSides = WindowInsetsSides.Top + WindowInsetsSides.Horizontal
private val BottomAndSides = WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
