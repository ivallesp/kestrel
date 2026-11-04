package app.kestrel.ui.screens.lot

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kestrel.ebay.MoneyText
import app.kestrel.engine.BidTiming
import app.kestrel.model.Money
import app.kestrel.ui.components.ConnectedToggleRow
import app.kestrel.ui.components.KestrelBottomSheet
import app.kestrel.ui.components.LotPlate
import app.kestrel.ui.components.MaxField
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.components.SumRow
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.armSummary
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.theme.PlateShapes
import app.kestrel.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant

/** The sum row's three amounts: "£120.00 + £6.50 = £126.50". */
@Immutable
data class SumFigures(
    val max: String,
    val postage: String,
    val total: String,
)

/** What the typed amount means for the sheet: the max, the error line, the sum row, odd pence and if Save works. */
@Immutable
data class EditMaxEvaluation(
    val max: Money?,
    val error: String?,
    val sum: SumFigures?,
    val oddPence: Money?,
    val armLine: String?,
    val canSave: Boolean,
)

/** The edit sheet's rules (DESIGN 3.18 and the edit-max sheet). Pure, so every case is unit-tested. */
object EditMaxForm {
    /** "Make it £90.37": odd pence beat bidders who stop at a round number. */
    const val ODD_PENCE_MINOR = 37L

    fun evaluate(
        text: String,
        sheet: EditSheetState,
        now: Instant,
    ): EditMaxEvaluation {
        val max = MoneyText.parseUserInput(text, sheet.currency)
        val minimum = sheet.minimum?.takeIf { it.currency == sheet.currency }
        val tooLow = max != null && minimum != null && max < minimum
        val error = if (tooLow) "Too low: eBay needs at least ${minimum.formatted()}" else sheet.error
        val postage = sheet.postage
        val sum =
            if (max != null &&
                postage != null
            ) {
                SumFigures(max.formatted(), postage.formatted(), (max + postage).formatted())
            } else {
                null
            }
        val setting = sheet.kind == SheetKind.Set
        val oddPence =
            if (setting && max != null &&
                max.minor % 100 == 0L
            ) {
                Money(max.minor + ODD_PENCE_MINOR, max.currency)
            } else {
                null
            }
        val armLine =
            if (setting) {
                val lead = sheet.selectedLead ?: BidTiming.MINIMUM_LEAD_SECONDS
                val bidAt = sheet.endAt.minusMillis(BidTiming.leadMillis(lead))
                if (max != null &&
                    !tooLow
                ) {
                    armSummary(max, bidAt, now)
                } else {
                    "No max yet: Kestrel will only watch the price."
                }
            } else {
                null
            }
        return EditMaxEvaluation(
            max = max,
            error = error,
            sum = sum,
            oddPence = oddPence,
            armLine = armLine,
            canSave = max != null && !tooLow && sheet.locked == null && !sheet.saving,
        )
    }
}

/**
 * The edit-max sheet (DESIGN "Edit-max sheet"), also used to raise a max the price passed and to set the first max of a
 * watched lot. Skips the half-open state, so the keyboard never hides Save.
 */
@Composable
fun EditMaxSheet(
    state: EditSheetState,
    input: TextFieldState,
    now: Instant,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onUseAmount: (Money) -> Unit,
    onSelectLead: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Cancel slides the sheet away before it leaves, as a swipe or a tap on the scrim does.
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    KestrelBottomSheet(
        title = state.title,
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        EditMaxSheetContent(
            state = state,
            input = input,
            now = now,
            onSave = onSave,
            onClose = close,
            onUseAmount = onUseAmount,
            onSelectLead = onSelectLead,
        )
    }
}

/** The inside of the edit sheet, below its title; screenshot tests draw it in a SheetLayout without a window. */
@Composable
fun EditMaxSheetContent(
    state: EditSheetState,
    input: TextFieldState,
    now: Instant,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onUseAmount: (Money) -> Unit,
    onSelectLead: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = input.text.toString()
    val form = remember(text, state, now) { EditMaxForm.evaluate(text, state, now) }
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        // The fields scroll on a short screen; Save and Cancel stay pinned above the keyboard.
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            SheetLotLine(state)
            Spacer(Modifier.height(16.dp))
            MaxField(
                state = input,
                minimum = (state.minimum ?: Money(1, state.currency)).formatted(),
                currencySymbol = state.currencySymbol,
                error = form.error,
                readOnly = state.locked != null,
                onDone = { if (form.canSave) onSave() },
            )
            form.sum?.let { sum ->
                Spacer(Modifier.height(12.dp))
                SumRow(max = sum.max, postage = sum.postage, total = sum.total)
            }
            form.oddPence?.let { suggestion ->
                Spacer(Modifier.height(12.dp))
                OddPenceCard(suggestion = suggestion, roundAmount = form.max, onUse = { onUseAmount(suggestion) })
            }
            if (state.leadOptions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LeadTimeChoice(state, onSelectLead)
            }
            val line = state.locked ?: form.armLine ?: state.deadline
            if (line != null) {
                Spacer(Modifier.height(12.dp))
                IconLine(
                    icon =
                        if (form.armLine != null &&
                            state.locked == null
                        ) {
                            Symbols.Schedule
                        } else {
                            Symbols.Timer
                        },
                    text = line,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = state.primary, onClick = onSave, enabled = form.canSave)
        TextButton(
            onClick = onClose,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.TouchTarget),
        ) {
            Text(
                if (state.locked !=
                    null
                ) {
                    "Close"
                } else {
                    "Cancel"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (state.saving) {
            Text(
                text = "Saving…",
                style = MaterialTheme.typography.bodySmall,
                color = inkSoft,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** The 40 dp plate, the title on one line and "£96.00 now · ends 18:21:40 GMT". */
@Composable
private fun SheetLotLine(state: EditSheetState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LotPlate(state.photo, Modifier.size(40.dp), shape = PlateShapes.Sheet, cutoutInset = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = state.lotTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.lotLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Make it £90.37" on a neutral card (rule MF17), with a tonal "Use". */
@Composable
private fun OddPenceCard(
    suggestion: Money,
    roundAmount: Money?,
    onUse: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Make it ${suggestion.formatted()}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                roundAmount?.let {
                    Text(
                        text = "Odd pence beat bidders who stop at ${it.formatted()}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onUse,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = Spacing.TouchTarget),
            ) {
                Text("Use", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** DESIGN S2 bid timing: the row, the 3 s / 6 s / 10 s toggles and the hint. */
@Composable
private fun LeadTimeChoice(
    state: EditSheetState,
    onSelectLead: (Int) -> Unit,
) {
    val selected = state.selectedLead
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "Bid timing",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            if (selected != null) {
                Text(
                    text = "${secondsText(selected.toLong())} before the end",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        ConnectedToggleRow(
            options = state.leadOptions.map { secondsText(it.toLong()) },
            selectedIndex = state.leadOptions.indexOf(selected),
            onSelect = { onSelectLead(state.leadOptions[it]) },
        )
        state.leadHint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A 16 dp glyph and one line of ink-soft text: the deadline, the arm summary or the lock. */
@Composable
private fun IconLine(
    icon: Int,
    text: String,
) {
    val style = MaterialTheme.typography.bodyMedium
    val iconTop = with(LocalDensity.current) { ((style.lineHeight.toDp() - 16.dp) / 2).coerceAtLeast(0.dp) }
    Row(verticalAlignment = Alignment.Top) {
        SymbolIcon(
            icon,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 16.dp,
            modifier = Modifier.padding(top = iconTop),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
