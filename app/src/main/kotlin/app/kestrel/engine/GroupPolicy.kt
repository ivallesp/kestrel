package app.kestrel.engine

import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeStatus

/**
 * "Win 1 of N" (or [SnipeGroup.targetWins] of N). Once the group has its wins the rest are cancelled. At fire time a
 * member is skipped while another member could still win: a bid of another member is out and unresolved, or another
 * member's window is preparing to fire first. Never risk winning more than the user asked for.
 */
object GroupPolicy {
    /** Members ending closer together than this may skip each other, so the UI warns about them. */
    const val MINIMUM_GAP_MILLIS: Long = 3 * 60_000L

    sealed interface Decision {
        data object Proceed : Decision

        data class Skip(
            val reason: String,
        ) : Decision
    }

    /** Statuses whose member will not bid again and can be cancelled when the group is complete. */
    private val cancellableStatuses =
        setOf(
            SnipeStatus.ARMED,
            SnipeStatus.PRICE_OVER_MAX,
            SnipeStatus.NEEDS_SIGN_IN,
            SnipeStatus.NEEDS_YOU,
            SnipeStatus.DRY_RUN_FAILED,
        )

    /** Before a member's window opens: skip it when the group already has its wins. */
    fun beforeWindow(
        snipe: Snipe,
        group: SnipeGroup,
        members: List<Snipe>,
    ): Decision {
        val others = members.filter { it.id != snipe.id }
        val won = others.filter { it.status == SnipeStatus.WON }
        return if (won.size >= group.targetWins) Decision.Skip(wonReason(group, won)) else Decision.Proceed
    }

    /** Right before firing: also skip while another member could still win. */
    fun atFire(
        snipe: Snipe,
        group: SnipeGroup,
        members: List<Snipe>,
    ): Decision {
        val others = members.filter { it.id != snipe.id }
        val won = others.filter { it.status == SnipeStatus.WON }
        if (won.size >= group.targetWins) return Decision.Skip(wonReason(group, won))
        val undecided =
            others.filter {
                it.status in BID_SENT_STATUSES ||
                    (it.status == SnipeStatus.BIDDING && firesBefore(it, snipe))
            }
        if (won.size + undecided.size >= group.targetWins) {
            val names = undecided.joinToString { "\"${it.title}\"" }
            return Decision.Skip(
                "$names in ${group.name} is not decided yet, so bidding now could win more items than the group allows.",
            )
        }
        return Decision.Proceed
    }

    /** Members to mark skipped once the group has its wins. Members already bidding are left to their own windows. */
    fun membersToCancel(
        group: SnipeGroup,
        members: List<Snipe>,
    ): List<Snipe> {
        val wonCount = members.count { it.status == SnipeStatus.WON }
        if (wonCount < group.targetWins) return emptyList()
        return members.filter { it.status in cancellableStatuses }
    }

    /** Pairs of members, in end order, that end less than [minimumGapMillis] apart. */
    fun closeEndings(
        members: List<Snipe>,
        minimumGapMillis: Long = MINIMUM_GAP_MILLIS,
    ): List<CloseEnding> =
        members
            .sortedWith(compareBy({ it.endTimeUtcMillis }, { it.id }))
            .zipWithNext()
            .map { (earlier, later) -> CloseEnding(earlier, later, later.endTimeUtcMillis - earlier.endTimeUtcMillis) }
            .filter { it.gapMillis < minimumGapMillis }

    // The member that fires first goes first; equal times fall back to the lower id, so both windows agree.
    private fun firesBefore(
        other: Snipe,
        snipe: Snipe,
    ): Boolean {
        val otherFires = BidTiming.fireAtUtcMillis(other.endTimeUtcMillis, other.leadSeconds)
        val snipeFires = BidTiming.fireAtUtcMillis(snipe.endTimeUtcMillis, snipe.leadSeconds)
        return otherFires < snipeFires || (otherFires == snipeFires && other.id < snipe.id)
    }

    private fun wonReason(
        group: SnipeGroup,
        won: List<Snipe>,
    ): String = "You already won ${won.joinToString { "\"${it.title}\"" }} in ${group.name}."
}

/** Two group members that end close together. */
data class CloseEnding(
    val earlier: Snipe,
    val later: Snipe,
    val gapMillis: Long,
)
