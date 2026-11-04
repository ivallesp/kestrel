package app.kestrel.platform

import app.kestrel.data.SnipeRules
import app.kestrel.engine.NeedsUserReason
import app.kestrel.engine.SnipeNotifier
import app.kestrel.engine.SnipeStore
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.StatusFamily
import app.kestrel.model.TimelineKind

/**
 * After each job: reschedule the snipe from its fresh state. A window that ends with the snipe still "armed" or
 * "bidding" never placed a bid and never reached a result, so the user is told instead of being left with a snipe
 * that looks armed (which would also open the window again and again). A window's Live Update is always closed,
 * even when the engine stopped before closing it, so no countdown outlives its window.
 */
internal class SnipeJobAftermath(
    private val store: SnipeStore,
    private val notifier: SnipeNotifier,
    private val reschedule: (snipe: Snipe, lastCheckAttemptUtcMillis: Long?) -> Unit,
    private val cancelSchedule: (snipeId: Long) -> Unit,
    private val nowUtcMillis: () -> Long,
    /** True when the scheduler has no check left to try for this snipe (its result checks are over). */
    private val nothingLeftToTry: (snipe: Snipe, lastCheckAttemptUtcMillis: Long?) -> Boolean = { _, _ -> false },
) : JobAftermath {
    override suspend fun jobFailed(
        snipeId: Long,
        kind: JobKind,
        message: String,
    ) {
        store.addEvent(snipeId, TimelineKind.PROBLEM, message)
    }

    override suspend fun jobFinished(
        snipeId: Long,
        kind: JobKind,
        startedAtUtcMillis: Long,
    ) {
        if (kind == JobKind.WINDOW) notifier.windowClosed(snipeId)
        val snipe = store.snipe(snipeId)
        if (snipe == null) {
            cancelSchedule(snipeId)
            return
        }
        val window = kind == JobKind.WINDOW
        // A window that closed because the end moved far away (or that started far too early) handed the snipe back
        // on purpose; a new window opens before the new end, so there is nothing to tell the user.
        val handedBack = window && snipe.status in windowNeverProgressed && SnipeRules.handedBack(snipe, nowUtcMillis())
        val windowStalled = window && snipe.status in windowNeverProgressed && !handedBack
        val lastAttempt = if (window) null else startedAtUtcMillis
        val settled =
            when {
                windowStalled -> stoppedWithoutBid(snipe)
                handedBack && snipe.status == SnipeStatus.BIDDING -> armedAgain(snipe)
                resultChecksOver(snipe, lastAttempt) -> gaveUp(snipe)
                else -> snipe
            }
        reschedule(settled, lastAttempt)
    }

    private fun resultChecksOver(
        snipe: Snipe,
        lastAttempt: Long?,
    ): Boolean =
        !snipe.status.isFinal && snipe.endTimeUtcMillis < nowUtcMillis() && nothingLeftToTry(snipe, lastAttempt)

    /**
     * The checks after the end are over and the result is still open. A bid that may be with eBay is never decided
     * for the user: the snipe keeps its status (its group stays blocked, never risking a second win) and the user is
     * asked to look on eBay and settle it. A snipe that never bid ends as not placed.
     */
    private suspend fun gaveUp(snipe: Snipe): Snipe {
        val bidMayBeOut = snipe.status.family == StatusFamily.BIDDING && snipe.status != SnipeStatus.BIDDING
        if (bidMayBeOut || snipe.result?.bidSentAtUtcMillis != null) {
            store.addEvent(snipe.id, TimelineKind.PROBLEM, RESULT_UNREAD)
            notifier.needsUser(snipe, NeedsUserReason.UNKNOWN_PAGE, RESULT_UNREAD)
            return snipe
        }
        val result = SnipeResult(SnipeOutcome.NOT_PLACED, snipe.lastPrice, null, null, null, null, NEVER_READ)
        store.update(snipe.copy(status = SnipeStatus.FAILED, result = result, updatedAtUtcMillis = nowUtcMillis()))
        store.addEvent(snipe.id, TimelineKind.RESULT, NEVER_READ)
        val ended = store.snipe(snipe.id) ?: snipe
        notifier.result(ended, result)
        return ended
    }

    // The window's own hand-back write did not land: the snipe goes back to armed now that no window holds it.
    private suspend fun armedAgain(snipe: Snipe): Snipe {
        store.update(snipe.copy(status = SnipeStatus.ARMED, updatedAtUtcMillis = nowUtcMillis()))
        return store.snipe(snipe.id) ?: snipe
    }

    private suspend fun stoppedWithoutBid(snipe: Snipe): Snipe {
        val stopped = snipe.copy(status = SnipeStatus.NEEDS_YOU, updatedAtUtcMillis = nowUtcMillis())
        store.update(stopped)
        store.addEvent(snipe.id, TimelineKind.PROBLEM, WINDOW_STOPPED)
        notifier.needsUser(stopped, NeedsUserReason.BID_FAILED, WINDOW_STOPPED)
        return store.snipe(snipe.id) ?: stopped
    }

    companion object {
        const val WINDOW_STOPPED = "The bid window closed without placing a bid. Open the listing to check it."
        const val RESULT_UNREAD =
            "Kestrel could not read the result of your bid. Check the listing on eBay, then mark it won or lost in " +
                "Kestrel; until then other snipes in its group wait."
        const val NEVER_READ = "The auction ended and Kestrel could not read it. Kestrel did not bid."
        private val windowNeverProgressed = setOf(SnipeStatus.ARMED, SnipeStatus.BIDDING)
    }
}
