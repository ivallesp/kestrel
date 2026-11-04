package app.kestrel.platform

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/**
 * Finishes a reschedule that did not fit in a broadcast's time limit. Android gives a receiver only a few seconds,
 * even with goAsync, so a slow database must not turn a reboot into an "app not responding".
 */
class RescheduleWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        try {
            applicationContext.kestrelContainer.scheduler.rescheduleAll()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Rescheduling failed; WorkManager will try again", error)
            Result.retry()
        }

    companion object {
        private const val TAG = "RescheduleWorker"
        const val UNIQUE_NAME = "reschedule-all"

        fun enqueue(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<RescheduleWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
