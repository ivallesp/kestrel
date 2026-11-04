package app.kestrel.ui.screens.group

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.BottomFade
import app.kestrel.ui.components.KestrelBottomSheet
import app.kestrel.ui.components.LotPlate
import app.kestrel.ui.components.OutlinedActionButton
import app.kestrel.ui.components.SectionHead
import app.kestrel.ui.components.StateLabel
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.format.plural
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.label
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.PlateShapes
import app.kestrel.ui.theme.Spacing
import java.time.Instant

/** What the user can do on the Group screen. Each method has a no-op default. */
@Stable
interface GroupActions {
    fun back() {}

    /** A member row: its detail, or its result once it has ended. */
    fun open(row: GroupLotRowState) {}

    /** A row's action: "Set a max", "Raise max", "Sign in" and the like. */
    fun act(
        snipeId: Long,
        action: LotAction,
    ) {}

    fun ungroup() {}

    /** "Add a snipe to this group": opens the picker of armed snipes. */
    fun addSnipe() {}
}

/**
 * One "win 1 of N" group (DESIGN Group screen): the top bar with the group's name and Ungroup, the rule card, and
 * its lots in a segmented block, with "Add a snipe to this group". Stateless; [menuOpen] opens the overflow menu
 * at first, for screenshots.
 */
@Composable
fun GroupScreen(
    state: GroupUiState,
    modifier: Modifier = Modifier,
    actions: GroupActions = remember { object : GroupActions {} },
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    menuOpen: Boolean = false,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(windowInsets)
                    .padding(bottom = Spacing.ScreenEnd),
            ) {
                GroupTopBar(state.title, state.canUngroup, menuOpen, actions)
                Column(Modifier.padding(horizontal = Spacing.ColumnA)) {
                    when (val content = state.content) {
                        GroupContent.Loading -> {}

                        GroupContent.Missing -> {
                            MissingGroup()
                        }

                        GroupContent.Empty -> {
                            EmptyGroup(onAdd = actions::addSnipe)
                        }

                        is GroupContent.Members -> {
                            Members(content, state.now, actions)
                        }
                    }
                }
            }
            SnackbarHost(
                snackbarHostState,
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(windowInsets),
            )
            BottomFade(windowInsets = windowInsets)
        }
    }
}

@Composable
private fun GroupTopBar(
    title: String,
    canUngroup: Boolean,
    menuOpen: Boolean,
    actions: GroupActions,
) {
    var expanded by rememberSaveable { mutableStateOf(menuOpen) }
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions::back) { SymbolIcon(Symbols.ArrowBack, contentDescription = "Back") }
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (canUngroup) {
            Box {
                IconButton(onClick = { expanded = true }) {
                    SymbolIcon(Symbols.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Ungroup", style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            expanded = false
                            actions.ungroup()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingGroup() {
    Column {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This group no longer exists.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** DESIGN 3.19 "Group with no lots". */
@Composable
private fun EmptyGroup(onAdd: () -> Unit) {
    Column {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No snipes in this group yet",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add two or more snipes, and Kestrel wins one and cancels the rest.",
            style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedActionButton(
            text = "Add a snipe to this group",
            onClick = onAdd,
            icon = Symbols.Add,
            height = Spacing.TouchTarget,
        )
    }
}

@Composable
private fun Members(
    content: GroupContent.Members,
    now: Instant,
    actions: GroupActions,
) {
    Column {
        Spacer(Modifier.height(8.dp))
        RuleCardView(content.rule)
        Spacer(Modifier.height(Spacing.Section))
        SectionHead(title = "In this group", count = plural(content.rows.size, "lot", "lots"))
        Spacer(Modifier.height(8.dp))
        Column {
            content.rows.forEachIndexed { index, row ->
                GroupLotRow(
                    row = row,
                    now = now,
                    index = index,
                    count = content.rows.size,
                    onOpen = { actions.open(row) },
                    onAction = { action -> actions.act(row.snipeId, action) },
                )
                if (index < content.rows.lastIndex) Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
            }
        }
        if (content.canAdd) {
            Spacer(Modifier.height(Spacing.BlockGap))
            OutlinedActionButton(
                text = "Add a snipe to this group",
                onClick = actions::addSnipe,
                icon = Symbols.Add,
                height = Spacing.TouchTarget,
            )
        }
    }
}

/**
 * The rule card (DESIGN Group screen item 2): neutral secondaryContainer for the rule, the Attention container when
 * two members bid too close together, the Won container after the win. Each tone has its own glyph.
 */
@Composable
private fun RuleCardView(rule: RuleCard) {
    val status = KestrelTheme.status
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer, glyphTint) =
        when (rule.tone) {
            RuleTone.Neutral -> {
                Triple(
                    scheme.secondaryContainer,
                    scheme.onSecondaryContainer,
                    scheme.onSecondaryContainer,
                )
            }

            RuleTone.Attention -> {
                Triple(
                    status.attention.container,
                    status.attention.onContainer,
                    status.attention.accent,
                )
            }

            RuleTone.Won -> {
                Triple(status.won.container, status.won.onContainer, status.won.accent)
            }
        }
    val glyph =
        when (rule.tone) {
            RuleTone.Neutral -> Symbols.Link
            RuleTone.Attention -> Symbols.Warning
            RuleTone.Won -> Symbols.Trophy
        }
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = onContainer,
    ) {
        Column(Modifier.padding(Spacing.CardInset).semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(glyph, tint = glyphTint, size = 24.dp)
                Spacer(Modifier.width(12.dp))
                Text(rule.headline, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(rule.body, style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading))
            rule.note?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading))
            }
        }
    }
}

/**
 * "Add a snipe to this group": a sheet of the armed snipes that may join. A tap adds one and closes the sheet.
 */
@Composable
fun SnipePickerSheet(
    groupName: String,
    candidates: List<GroupCandidate>,
    now: Instant,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KestrelBottomSheet(title = "Add to $groupName", onDismissRequest = onDismiss, modifier = modifier) {
        SnipePickerBody(candidates, now, onPick)
    }
}

/** The inside of [SnipePickerSheet], below its title; screenshot tests draw it in a sheet frame. */
@Composable
fun SnipePickerBody(
    candidates: List<GroupCandidate>,
    now: Instant,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (candidates.isEmpty()) {
            Text(
                text = "No other armed snipe can join this group now.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "Choose an armed snipe. It keeps its own max and bid time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            candidates.forEachIndexed { index, candidate ->
                SegmentedListItem(
                    onClick = { onPick(candidate.snipeId) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = candidates.size),
                    // On the sheet's surfaceContainerLow the card colour would barely show in dark theme.
                    colors =
                        ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    leadingContent = {
                        LotPlate(candidate.photo, Modifier.size(40.dp), shape = PlateShapes.Sheet, cutoutInset = 4.dp)
                    },
                    supportingContent = {
                        StateLabel(candidate.status.tone, candidate.status.label(now), Modifier.padding(top = 2.dp))
                    },
                ) {
                    Text(
                        text = candidate.title.forDisplay(),
                        style = KestrelTheme.type.lotTitleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (index < candidates.lastIndex) Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
