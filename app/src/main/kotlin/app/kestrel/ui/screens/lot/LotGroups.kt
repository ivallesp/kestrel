package app.kestrel.ui.screens.lot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.kestrel.engine.GroupPolicy
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeStatus
import app.kestrel.ui.components.KestrelBottomSheet
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.components.SectionHead
import app.kestrel.ui.format.plural
import app.kestrel.ui.theme.KestrelTheme

/**
 * A group this lot can move into, for the lot's group sheet: its name, how many lots it has, and why it cannot take
 * this one ([blocked]: it already has its win), or a warning that the lot ends within three minutes of a member, so
 * one of the two may be skipped.
 */
@Immutable
data class GroupChoice(
    val groupId: Long,
    val name: String,
    val lots: Int,
    val blocked: String? = null,
    val endsClose: Boolean = false,
)

/** The lot's group sheet: which groups it may join, the name typed for a new one, and the group it is in now. */
@Immutable
data class GroupSheetState(
    val currentGroup: String?,
    val choices: List<GroupChoice>,
)

/**
 * The groups [snipe] could move into: every group but its own, with their open members. A group that already won
 * takes no one (the engine would cancel the newcomer before it bids).
 */
object LotGroupChoices {
    fun of(
        snipe: Snipe,
        groups: List<SnipeGroup>,
        snipes: List<Snipe>,
        wins: Map<Long, Int>,
    ): List<GroupChoice> =
        groups
            .filter { it.id != snipe.groupId }
            .sortedBy { it.name.lowercase() }
            .map { group ->
                val members = snipes.filter { it.groupId == group.id }
                val won = maxOf(wins[group.id] ?: 0, members.count { it.status == SnipeStatus.WON })
                val open = members.filter { !it.status.isFinal }
                GroupChoice(
                    groupId = group.id,
                    name = group.name,
                    lots = members.size,
                    blocked = "Already won one".takeIf { won >= group.targetWins },
                    endsClose =
                        GroupPolicy.closeEndings(open + snipe).any {
                            snipe.id in
                                setOf(it.earlier.id, it.later.id)
                        },
                )
            }
}

/**
 * "Add to a group" / "Move to another group": the groups the lot may join, then a new group by name. The lot keeps
 * its max, its bid time and its timeline wherever it goes.
 */
@Composable
fun GroupSheet(
    state: GroupSheetState,
    newName: TextFieldState,
    onChoose: (Long) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (state.currentGroup == null) "Add to a group" else "Move to another group"
    KestrelBottomSheet(title = title, onDismissRequest = onDismiss, modifier = modifier) {
        GroupSheetBody(state, newName, onChoose, onCreate)
    }
}

/** The inside of [GroupSheet], below its title; screenshot tests draw it in a sheet frame. */
@Composable
fun GroupSheetBody(
    state: GroupSheetState,
    newName: TextFieldState,
    onChoose: (Long) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        Text(
            text =
                state.currentGroup?.let { "Now in $it. It keeps its max and bid time." }
                    ?: "Once one lot of a group is won, Kestrel cancels the rest. This lot keeps its max and bid time.",
            style = MaterialTheme.typography.bodyMedium,
            color = inkSoft,
        )
        if (state.choices.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            state.choices.forEachIndexed { index, choice ->
                SegmentedListItem(
                    onClick = { onChoose(choice.groupId) },
                    enabled = choice.blocked == null,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = state.choices.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = KestrelTheme.catalogue.card),
                    supportingContent = {
                        Text(
                            text =
                                listOfNotNull(
                                    plural(choice.lots, "lot", "lots"),
                                    choice.blocked,
                                    "Ends close to a lot in it".takeIf { choice.endsClose && choice.blocked == null },
                                ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = inkSoft,
                        )
                    },
                ) {
                    Text(choice.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionHead("New group", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            state = newName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Group name") },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            onKeyboardAction = { if (newName.text.isNotBlank()) onCreate() },
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(text = "Start the group", onClick = onCreate, enabled = newName.text.isNotBlank())
    }
}
