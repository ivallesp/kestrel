package app.kestrel.ui.screens.group

import androidx.compose.runtime.Immutable
import app.kestrel.model.Money
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotStatus
import java.time.Instant

/** Everything the Group screen shows at [now] (DESIGN "Group screen"). The ViewModel ticks [now]. */
@Immutable
data class GroupUiState(
    val now: Instant,
    /** The group's name, or "Group" while it loads and when it no longer exists. */
    val title: String,
    val content: GroupContent,
    /** Snipes that may join the group: armed, not bidding, and in no other group or another one. */
    val candidates: List<GroupCandidate> = emptyList(),
    /** True when there are open snipes to take out of the group. */
    val canUngroup: Boolean = false,
)

@Immutable
sealed interface GroupContent {
    data object Loading : GroupContent

    data object Missing : GroupContent

    /** "No snipes in this group yet". */
    data object Empty : GroupContent

    /** [canAdd] is false once the group has its wins: a new member would only be cancelled. */
    data class Members(
        val rule: RuleCard,
        val rows: List<GroupLotRowState>,
        val canAdd: Boolean = true,
    ) : GroupContent
}

/** How the rule card reads: the rule itself, a warning about close endings, or the win. */
enum class RuleTone { Neutral, Attention, Won }

/** The rule card (DESIGN Group screen item 2): "Win 1 of 2", what happens, and whether the timing is safe. */
@Immutable
data class RuleCard(
    val tone: RuleTone,
    val headline: String,
    val body: String,
    val note: String? = null,
)

/** One lot row of the group (DESIGN 3.5, without the group flag). Words are read from [status] at now. */
@Immutable
data class GroupLotRowState(
    val snipeId: Long,
    val title: String,
    val photo: LotPhoto,
    val status: LotStatus,
    val price: Money?,
    val bidCount: Int?,
    val postage: Money?,
    val endsAt: Instant,
    val extendedBidding: Boolean,
    /** When the most recent check failed, for "Couldn't check at 18:05 · retrying". */
    val failedCheckAt: Instant? = null,
)

/** A snipe the picker offers to add to the group. */
@Immutable
data class GroupCandidate(
    val snipeId: Long,
    val title: String,
    val photo: LotPhoto,
    val status: LotStatus,
)
