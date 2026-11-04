package app.kestrel.engine

import app.kestrel.model.Snipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind

/** Applies [GroupPolicy] with what the store says about the group right now. */
internal class GroupGate(
    private val store: SnipeStore,
    private val writer: SnipeWriter,
    private val journal: EngineJournal,
) {
    /**
     * The group decision for [snipe]. When the group cannot be read at fire time the answer is to skip: without the
     * other members Kestrel cannot rule out winning more than the user asked for. A deleted group puts no limit.
     */
    suspend fun decide(
        snipe: Snipe,
        atFire: Boolean,
    ): GroupPolicy.Decision {
        val groupId = snipe.groupId ?: return GroupPolicy.Decision.Proceed
        val groupRead = callWithin(WindowTimings.STORE_TIMEOUT) { store.group(groupId) }
        if (groupRead is Call.Done && groupRead.value == null) {
            journal.event(snipe.id, TimelineKind.PROBLEM, "The snipe's group no longer exists, so it bids on its own.")
            return GroupPolicy.Decision.Proceed
        }
        val group = groupRead.valueOrNull()
        val members = callWithin(WindowTimings.STORE_TIMEOUT) { store.groupMembers(groupId) }.valueOrNull()
        if (group == null || members == null) {
            return if (atFire) {
                GroupPolicy.Decision.Skip("Kestrel could not read the other snipes in the group, so it did not bid.")
            } else {
                GroupPolicy.Decision.Proceed
            }
        }
        return if (atFire) {
            GroupPolicy.atFire(
                snipe,
                group,
                members,
            )
        } else {
            GroupPolicy.beforeWindow(snipe, group, members)
        }
    }

    /** Marks [snipe] skipped when its group already has its wins; returns the skipped snipe, or null to go on. */
    suspend fun skipIfComplete(snipe: Snipe): Snipe? {
        if (snipe.isWatchOnly) return null
        val decision = decide(snipe, atFire = false)
        if (decision !is GroupPolicy.Decision.Skip) return null
        return markSkipped(snipe, decision.reason)
    }

    /** After [winner] won: the members that have not started bidding are skipped. */
    suspend fun cancelRestAfterWin(winner: Snipe) {
        val groupId = winner.groupId ?: return
        val group = callWithin(WindowTimings.STORE_TIMEOUT) { store.group(groupId) }.valueOrNull() ?: return
        val members = callWithin(WindowTimings.STORE_TIMEOUT) { store.groupMembers(groupId) }.valueOrNull() ?: return
        val reason = "You won \"${winner.title}\" in ${group.name}."
        for (member in GroupPolicy.membersToCancel(group, members)) {
            if (member.id != winner.id) markSkipped(member, reason)
        }
    }

    // A member whose window started meanwhile is left to that window, whose own group check skips it at fire time.
    private suspend fun markSkipped(
        snipe: Snipe,
        reason: String,
    ): Snipe? {
        val skipped = SnipeResult(SnipeOutcome.SKIPPED_BY_GROUP, null, null, null, null, null, reason)
        val updated =
            writer.updateIfIdle(snipe.id) { it.copy(status = SnipeStatus.SKIPPED_BY_GROUP, result = skipped) }
                ?: return null
        journal.event(snipe.id, TimelineKind.CANCELLED, "Skipped: $reason")
        journal.notify { result(updated, skipped) }
        return updated
    }
}
