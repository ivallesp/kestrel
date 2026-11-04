package app.kestrel.platform

import android.content.Context
import app.kestrel.AppContainer
import app.kestrel.data.AddResult
import app.kestrel.data.EditResult
import app.kestrel.data.SnipeRepository
import app.kestrel.model.Listing
import app.kestrel.model.Money
import app.kestrel.model.Snipe

/**
 * What the UI (and the notification buttons) do to a snipe. Each change is stored first, then the snipe's alarms,
 * running jobs and notifications follow, so no caller can forget to reschedule or leave a cancelled window running.
 * Reads stay on [SnipeRepository].
 */
class SnipeActions internal constructor(
    private val repository: SnipeRepository,
    private val reschedule: (Snipe) -> Unit,
    private val cancelSchedule: (Long) -> Unit,
    private val stopJobs: (Long) -> Unit,
    private val clearNotifications: (Long) -> Unit,
) {
    constructor(context: Context) : this(context.kestrelContainer)

    private constructor(container: AppContainer) : this(
        repository = container.repository,
        reschedule = { container.scheduler.schedule(it) },
        cancelSchedule = { container.scheduler.cancel(it) },
        stopJobs = { container.jobRegistry.cancelAll(it) },
        clearNotifications = { container.notifications.cancelForSnipe(it) },
    )

    suspend fun add(
        listing: Listing,
        maxBid: Money?,
        leadSeconds: Int,
        groupId: Long? = null,
    ): AddResult =
        repository.addFromListing(listing, maxBid, leadSeconds, groupId).also { result ->
            if (result is AddResult.Added) reschedule(result.snipe)
        }

    suspend fun updateMax(
        snipeId: Long,
        maxBid: Money?,
    ): EditResult = repository.updateMax(snipeId, maxBid).rescheduled()

    suspend fun setLead(
        snipeId: Long,
        leadSeconds: Int,
    ): EditResult = repository.setLead(snipeId, leadSeconds).rescheduled()

    suspend fun setGroup(
        snipeId: Long,
        groupId: Long?,
    ): EditResult = repository.setGroup(snipeId, groupId).rescheduled()

    /** Stored before anything else, so an engine that re-reads the snipe already sees it cancelled. */
    suspend fun cancel(snipeId: Long): EditResult = repository.cancel(snipeId).alsoWhenDone { stopEverything(snipeId) }

    suspend fun delete(snipeId: Long): EditResult = repository.delete(snipeId).alsoWhenDone { stopEverything(snipeId) }

    suspend fun undoDelete(snipeId: Long): EditResult = repository.undoDelete(snipeId).rescheduled()

    private fun stopEverything(snipeId: Long) {
        stopJobs(snipeId)
        cancelSchedule(snipeId)
        clearNotifications(snipeId)
    }

    private fun EditResult.rescheduled(): EditResult = alsoWhenDone { reschedule(it) }

    private inline fun EditResult.alsoWhenDone(action: (Snipe) -> Unit): EditResult {
        if (this is EditResult.Done) action(snipe)
        return this
    }
}
