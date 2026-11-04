package app.kestrel.platform

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.kestrel.engine.SnipeStore
import app.kestrel.model.Snipe
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/** A check more than an hour before the end, left to WorkManager so it can wait for a network and a maintenance window. */
class CheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val snipeId = inputData.getLong(KEY_SNIPE_ID, 0L)
        if (snipeId <= 0L) return Result.failure()
        return try {
            applicationContext.kestrelContainer.backgroundCheck.run(snipeId)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The check itself never throws; this is the database or the scheduler, worth one more try later.
            Log.e(WORKER_TAG, "Background check of snipe $snipeId could not finish", error)
            Result.retry()
        }
    }

    companion object {
        const val KEY_SNIPE_ID = "snipe_id"
        const val TAG = "kestrel-check"
        private const val WORKER_TAG = "CheckWorker"

        fun uniqueName(snipeId: Long): String = "check-$snipeId"

        internal fun request(
            snipeId: Long,
            delayMillis: Long,
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<CheckWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_SNIPE_ID to snipeId))
                .addTag(TAG)
                .build()
    }
}

/**
 * The body of [CheckWorker]: load the listing on the main thread (WebViews need it), run the engine's check, and
 * schedule what comes next. A failed check still counts as an attempt, so the next one waits its normal interval.
 */
internal class BackgroundCheck(
    private val runner: SnipeJobRunner,
    private val store: SnipeStore,
    private val reschedule: (snipe: Snipe, lastCheckAttemptUtcMillis: Long?) -> Unit,
    private val jobs: SnipeJobRegistry,
    private val nowUtcMillis: () -> Long,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    suspend fun run(snipeId: Long) {
        val snipe = store.snipe(snipeId) ?: return
        if (snipe.status.isFinal) return
        val startedAt = nowUtcMillis()
        // The service already has this listing open (a window or a dry run): a second page would only compete.
        if (!jobs.isRunning(snipeId)) {
            try {
                withTimeout(JobKind.CHECK.timeoutMillis) {
                    withContext(mainDispatcher) { runner.run(JobKind.CHECK, snipeId) }
                }
            } catch (error: TimeoutCancellationException) {
                val minutes = JobKind.CHECK.timeoutMillis / 60_000
                store.addEvent(snipeId, TimelineKind.PROBLEM, "Background check stopped after $minutes min.")
            } catch (error: CancellationException) {
                // WorkManager stopped the work; it runs it again when its constraints allow.
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Background check of snipe $snipeId failed", error)
                store.addEvent(snipeId, TimelineKind.PROBLEM, "Background check failed: ${error.javaClass.simpleName}")
            }
        }
        // Scheduling replaces this very work, which cancels it; that is why it is the last thing done.
        store.snipe(snipeId)?.let { reschedule(it, startedAt) }
    }

    private companion object {
        const val TAG = "BackgroundCheck"
    }
}
