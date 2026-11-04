package app.kestrel.platform

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Receives every snipe alarm. An exact alarm allows a foreground-service start for about ten seconds, so the
 * service is started at once and does all the work; nothing slow happens here.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val request = SnipeRequest.from(intent) ?: return
        forwardToService(
            context = context,
            request = request,
            startService = { ContextCompat.startForegroundService(context, it) },
            onBlocked = { context.kestrelContainer.notifications.showTapToArm(it) },
        )
    }

    companion object {
        private const val TAG = "AlarmReceiver"

        /** Returns false when Android refused the start; the user then gets a notification to tap instead. */
        internal fun forwardToService(
            context: Context,
            request: SnipeRequest,
            startService: (Intent) -> Unit,
            onBlocked: (SnipeRequest) -> Unit,
        ): Boolean =
            try {
                startService(SnipeService.intent(context, request))
                true
            } catch (error: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Android refused to start the service for ${request.action}", error)
                onBlocked(request)
                false
            } catch (error: IllegalStateException) {
                Log.w(TAG, "The service could not start for ${request.action}", error)
                onBlocked(request)
                false
            } catch (error: SecurityException) {
                Log.e(TAG, "The service could not start for ${request.action}", error)
                onBlocked(request)
                false
            }
    }
}
