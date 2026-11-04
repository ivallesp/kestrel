package app.kestrel.platform

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal enum class Submission {
    STARTED,

    /** The same job for the same snipe is already running (for example the backup alarm during the window). */
    ALREADY_RUNNING,

    /** A more important job for the snipe is running and already does this one's work. */
    COVERED_BY_RUNNING_JOB,
}

/** What happens around a job: problems go to the timeline, and the snipe is rescheduled after every job. */
internal interface JobAftermath {
    suspend fun jobFailed(
        snipeId: Long,
        kind: JobKind,
        message: String,
    )

    suspend fun jobFinished(
        snipeId: Long,
        kind: JobKind,
        startedAtUtcMillis: Long,
    )
}

/**
 * Runs the service's jobs: one coroutine (and one page) per job, several snipes at once, never the same job twice
 * for one snipe. Each job holds its own wake lock and has a hard timeout.
 */
internal class SnipeJobCoordinator(
    private val scope: CoroutineScope,
    private val runner: SnipeJobRunner,
    private val registry: SnipeJobRegistry,
    private val wakeLocks: WakeLocks,
    private val aftermath: JobAftermath,
    private val nowUtcMillis: () -> Long,
    private val onJobsChanged: () -> Unit,
    /**
     * Writes the WebView cookies to disk (CookieManager.flush). Chromium writes them every 30 s, but the service
     * stops right after the last job and a cached process is frozen within seconds: a session cookie eBay rotated
     * during the job would be lost with it, and the next snipe would find the user signed out.
     */
    private val flushCookies: suspend () -> Unit = {},
) {
    fun submit(request: SnipeRequest): Submission {
        val kind = request.action.jobKind
        val key = JobKey(request.snipeId, kind)
        val running = registry.runningKinds(request.snipeId)
        if (kind in running) return Submission.ALREADY_RUNNING
        if (running.any { it.rank > kind.rank }) return Submission.COVERED_BY_RUNNING_JOB
        // A window or dry run reloads the listing anyway; a lesser job still loading it would only compete.
        running.filter { it.rank < kind.rank }.forEach { registry.cancel(JobKey(request.snipeId, it)) }

        val job = scope.launch(start = CoroutineStart.LAZY) { run(request, kind, key) }
        if (!registry.register(key, request, job)) {
            job.cancel()
            return Submission.ALREADY_RUNNING
        }
        job.start()
        safely("update the service") { onJobsChanged() }
        return Submission.STARTED
    }

    private suspend fun run(
        request: SnipeRequest,
        kind: JobKind,
        key: JobKey,
    ) {
        // Everything below is guarded: one failing job must never take the other windows down with the process,
        // and its registry entry must always go, or the service would never stop.
        val startedAt = safely("read the clock") { nowUtcMillis() } ?: System.currentTimeMillis()
        val wakeLock =
            safely("take a wake lock") { wakeLocks.acquire("kestrel:${kind.name.lowercase()}", kind.wakeLockMillis) }
        try {
            withTimeout(kind.timeoutMillis) { runner.run(kind, request.snipeId) }
        } catch (error: TimeoutCancellationException) {
            val minutes = kind.timeoutMillis / 60_000
            report(request.snipeId, kind, "${kind.label} stopped after $minutes min without finishing.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "${kind.label} for snipe ${request.snipeId} failed", error)
            report(request.snipeId, kind, "${kind.label} stopped by an error: ${describe(error)}")
        } finally {
            safely("release the wake lock") { wakeLock?.release() }
            // The job stays registered until its aftermath is done, so nothing reopens a window that is still
            // settling its status.
            withContext(NonCancellable) {
                try {
                    aftermath.jobFinished(request.snipeId, kind, startedAt)
                } catch (error: Exception) {
                    Log.e(TAG, "Could not reschedule snipe ${request.snipeId} after ${kind.label}", error)
                }
                // Before the job leaves the registry, so the service cannot stop before the cookies are on disk.
                try {
                    flushCookies()
                } catch (error: Exception) {
                    Log.w(TAG, "Could not write the cookies after ${kind.label}", error)
                }
            }
            registry.unregister(key, currentCoroutineContext().job)
            safely("update the service") { onJobsChanged() }
        }
    }

    private inline fun <T> safely(
        what: String,
        action: () -> T,
    ): T? =
        try {
            action()
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not $what", error)
            null
        }

    private suspend fun report(
        snipeId: Long,
        kind: JobKind,
        message: String,
    ) {
        try {
            aftermath.jobFailed(snipeId, kind, message)
        } catch (error: Exception) {
            Log.e(TAG, "Could not record a problem for snipe $snipeId", error)
        }
    }

    private fun describe(error: Exception): String = error.javaClass.simpleName + (error.message?.let { ": $it" } ?: "")

    private companion object {
        const val TAG = "SnipeJobCoordinator"
    }
}
