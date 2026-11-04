package app.kestrel.platform

import app.kestrel.data.SnipeRules
import app.kestrel.engine.NeedsUserReason
import app.kestrel.engine.SnipeNotifier
import app.kestrel.engine.SnipeStore
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
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
        val settled =
            when {
                windowStalled -> stoppedWithoutBid(snipe)
                handedBack && snipe.status == SnipeStatus.BIDDING -> armedAgain(snipe)
                else -> snipe
            }
        reschedule(settled, if (window) null else startedAtUtcMillis)
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
        private val windowNeverProgressed = setOf(SnipeStatus.ARMED, SnipeStatus.BIDDING)
    }
}
