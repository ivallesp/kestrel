package app.kestrel.ui.screens.add

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.KestrelBottomSheet
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.format.plural

/** The title of the group picker sheet. */
const val GROUP_PICKER_TITLE = "Add to a group"

/**
 * The group picker sheet (DESIGN Group screen, "Group picker sheet"), opened from S2's group row: "No group" (the
 * default), each group with its open snipes, or "New group…", which adds a name field. "Done" keeps the choice.
 */
@Composable
fun GroupPickerSheet(
    state: GroupPickerState,
    onSelect: (GroupPick) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KestrelBottomSheet(title = GROUP_PICKER_TITLE, onDismissRequest = onDismiss, modifier = modifier) {
        GroupPickerBody(state = state, onSelect = onSelect, onDone = onDone)
    }
}

/** The inside of [GroupPickerSheet], below its title; screenshot tests draw it in a sheet frame. */
@Composable
fun GroupPickerBody(
    state: GroupPickerState,
    onSelect: (GroupPick) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows =
        buildList {
            add(GroupPick.NoGroup to "No group")
            state.options.forEach {
                add(GroupPick.Existing(it.id) to "${it.name} · ${plural(it.openSnipes, "snipe", "snipes")}")
            }
            add(GroupPick.New to "New group…")
        }
    val newSelected = state.selected == GroupPick.New
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.selectableGroup()) {
            rows.forEachIndexed { index, (pick, label) ->
                PickerRow(
                    label = label,
                    selected = state.selected == pick,
                    index = index,
                    count = rows.size,
                    onClick = { onSelect(pick) },
                )
                if (index < rows.lastIndex) Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
            }
        }
        if (newSelected) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                state = state.newName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Group name") },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                onKeyboardAction = { onDone() },
                shape = MaterialTheme.shapes.medium,
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Done",
            onClick = onDone,
            enabled = !newSelected || state.newName.text.isNotBlank(),
        )
    }
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    // On the sheet's surfaceContainerLow the card colour would barely show in dark theme.
    val row = MaterialTheme.colorScheme.surfaceContainerHigh
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = row, selectedContainerColor = row),
        contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                modifier = Modifier.padding(8.dp),
            )
        },
        modifier = Modifier.radioRole(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
