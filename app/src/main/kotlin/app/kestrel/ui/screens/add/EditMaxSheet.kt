package app.kestrel.ui.screens.add

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kestrel.ui.components.KestrelBottomSheet
import app.kestrel.ui.components.LotPlate
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.PlateShapes
import kotlinx.coroutines.launch

/**
 * The edit-max sheet (DESIGN "Edit-max sheet", also Raise max and Set a max), for Home, Lot and the screens a
 * notification opens. Compose it while a snipe is being edited and remove it in [onDismiss]:
 *
 * ```
 * var editing by rememberSaveable { mutableStateOf<Long?>(null) }
 * editing?.let { id -> EditMaxSheet(id, navigator, onDismiss = { editing = null }, mode = EditMaxMode.Raise) }
 * ```
 *
 * [mode] is the variant the user asked for ("Edit max", "Raise max", "Set a max"); null lets the snipe decide. A
 * save leaves its snackbar ("Max changed to £130.00", with Undo while the edits are open) through
 * [KestrelNavigator.leaveMessage], so the screen under the sheet, which collects the navigator's messages, shows it.
 * The ViewModel lives with that screen, so a save finishes even as the sheet slides away.
 */
@Composable
fun EditMaxSheet(
    snipeId: Long,
    navigator: KestrelNavigator,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    mode: EditMaxMode? = null,
    viewModel: EditMaxViewModel =
        kestrelViewModel(key = "edit-max-$snipeId") { container ->
            EditMaxViewModel(snipeId, ContainerEditMaxServices(container))
        },
) {
    // The state KestrelBottomSheet makes by default, held here so a save can slide the sheet away first.
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val reduceMotion = KestrelTheme.reduceMotion
    val currentDismiss by rememberUpdatedState(onDismiss)
    val close: () -> Unit =
        remember(sheetState, scope, reduceMotion) {
            {
                if (reduceMotion) {
                    currentDismiss()
                } else {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { currentDismiss() }
                }
            }
        }
    // Opened once per showing of the sheet; the state of an earlier opening is never drawn.
    val sessionId = remember(viewModel, mode) { viewModel.open(mode) }
    LaunchedEffect(viewModel, navigator) {
        viewModel.done.collect { done ->
            done.message?.let(navigator::leaveMessage)
            close()
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shown = state?.takeIf { it.sessionId == sessionId } ?: return
    val actions =
        remember(viewModel, close) {
            object : EditMaxActions {
                override fun save() = viewModel.save()

                override fun dismiss() = close()

                override fun useOddPence() = viewModel.useOddPence()

                override fun selectLead(index: Int) = viewModel.selectLead(index)
            }
        }
    KestrelBottomSheet(
        title = shown.title,
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        EditMaxSheetBody(state = shown, actions = actions)
    }
    shown.wordsCheck?.let { check ->
        WordsCheckDialog(check, onConfirm = viewModel::confirmWords, onDismiss = viewModel::dismissWords)
    }
}

/** What the user can do in the edit sheet. */
@Stable
interface EditMaxActions {
    fun save() {}

    fun dismiss() {}

    fun useOddPence() {}

    fun selectLead(index: Int) {}
}

/**
 * The inside of the edit sheet, below its title (DESIGN "Edit-max sheet" items 2 to 6): the lot line, the max field,
 * the sum row, the deadline, then "Save max" and "Cancel". The content scrolls; the buttons stay pinned above the
 * keyboard. Screenshot tests draw it in a sheet frame.
 */
@Composable
fun EditMaxSheetBody(
    state: EditMaxUiState,
    modifier: Modifier = Modifier,
    actions: EditMaxActions = remember { object : EditMaxActions {} },
    focusMax: Boolean = true,
) {
    val maxFocus = remember { FocusRequester() }
    if (focusMax && !state.blocked) {
        LaunchedEffect(state.mode) { maxFocus.requestFocus() }
    }
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            SheetLot(state.lot)
            Spacer(Modifier.height(16.dp))
            MaxEntryField(state.max, onDone = actions::save, modifier = Modifier.focusRequester(maxFocus))
            Spacer(Modifier.height(12.dp))
            WorstCaseRow(state.worstCase)
            state.oddPence?.let { oddPence ->
                Spacer(Modifier.height(16.dp))
                OddPenceCard(oddPence, onUse = actions::useOddPence)
            }
            state.timing?.let { timing ->
                Spacer(Modifier.height(16.dp))
                BidTimingSection(timing, onSelect = actions::selectLead)
            }
            Spacer(Modifier.height(12.dp))
            SheetLineRow(state.line)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = state.primaryLabel, onClick = actions::save, enabled = state.primaryEnabled)
        InkTextButton(state.dismissLabel, onClick = actions::dismiss, modifier = Modifier.fillMaxWidth())
    }
}

/** The 40 dp plate, the title on one line, and "£96.00 now · ends 18:21:40 GMT". */
@Composable
private fun SheetLot(lot: SheetLotLine) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LotPlate(lot.photo, Modifier.size(40.dp), shape = PlateShapes.Sheet, cutoutInset = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = lot.title.forDisplay(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = lot.line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The deadline, the plan, the lock or the refusal, with its glyph. Every time-to-bid line has the timer, as on the
 * hero and the rows. Only a plan sets its clock time in tabular figures (it changes with the lead time). The lock and
 * a refusal say the same thing (the max can't change inside the bid window), so they read the same: the warning
 * glyph and the Attention accent.
 */
@Composable
private fun SheetLineRow(line: SheetLine) {
    val refused = line.kind == SheetLineKind.Refused || line.kind == SheetLineKind.Locked
    val icon = if (refused) Symbols.Warning else Symbols.Timer
    val attention = KestrelTheme.status.attention.accent
    Row(verticalAlignment = Alignment.Top) {
        SymbolIcon(
            icon,
            tint = if (refused) attention else MaterialTheme.colorScheme.onSurfaceVariant,
            size = 16.dp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = figureLine(line.text, line.figure, tabular = line.kind == SheetLineKind.Plan),
            style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
            color = if (refused) attention else MaterialTheme.colorScheme.onSurface,
        )
    }
}
