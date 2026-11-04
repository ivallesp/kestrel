package app.kestrel.platform

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.kestrel.AppContainer
import app.kestrel.R
import app.kestrel.model.Snipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The specialUse foreground service that runs checks, dry runs and bid windows. Jobs run on the main thread because
 * WebViews must; several snipes can run at once, each with its own page. While a window runs, its Live Update is the
 * foreground notification; otherwise a quiet notification on the background channel is.
 */
class SnipeService : Service() {
    // A job that fails in a way nobody caught is logged, not allowed to kill the process and every other window.
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate +
                CoroutineExceptionHandler { _, error -> Log.e(TAG, "A snipe job failed unexpectedly", error) },
        )
    private lateinit var container: AppContainer
    private lateinit var coordinator: SnipeJobCoordinator
    private var lastStartId = 0
    private var foregroundNotificationId: Int? = null
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        container = kestrelContainer
        coordinator =
            SnipeJobCoordinator(
                scope = scope,
                runner = container.jobRunner,
                registry = container.jobRegistry,
                wakeLocks = container.wakeLocks,
                aftermath = container.jobAftermath,
                nowUtcMillis = { container.clock.nowUtcMillis() },
                onJobsChanged = ::onJobsChanged,
                flushCookies = container.flushCookies,
                keepWindowAlarmed = container.keepWindowAlarmed,
            )
        // A start after Android ended the process mid-window says why in that snipe's timeline (best effort).
        container.applicationScope.launch {
            try {
                container.lastExitNote.noteIfNew()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not note why the last process ended", error)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        lastStartId = startId
        val request = intent?.let { SnipeRequest.from(it) }
        // Android requires startForeground soon after startForegroundService, even for a duplicate; doing it
        // first also puts the countdown on screen at once.
        if (!enterForeground(request)) {
            stopWhenIdle()
            return START_NOT_STICKY
        }
        if (request == null) {
            Log.w(TAG, "Ignored a start without a valid snipe request")
        } else {
            val submission = coordinator.submit(request)
            if (submission != Submission.STARTED) Log.i(TAG, "${request.action} for ${request.snipeId}: $submission")
        }
        stopWhenIdle()
        // Not sticky: a killed window is recovered by its backup alarm and by rescheduling, never by a late restart.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(request: SnipeRequest?): Boolean {
        val (id, notification) = foregroundNotificationOrFallback(request)
        return try {
            ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            foregroundNotificationId = id
            true
        } catch (error: RuntimeException) {
            // ForegroundServiceStartNotAllowedException and the service-type exceptions all land here.
            Log.e(TAG, "Could not start in the foreground", error)
            request?.let { container.notifications.showTapToArm(it) }
            false
        }
    }

    /**
     * startForeground must be called whatever happens, or Android stops the app a few seconds later, so a
     * notification that cannot be built falls back to a plain one.
     */
    private fun foregroundNotificationOrFallback(request: SnipeRequest?): Pair<Int, Notification> =
        try {
            foregroundNotification(request)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not build the foreground notification", error)
            NotificationIds.SERVICE to
                Notification
                    .Builder(this, NotificationChannels.BACKGROUND)
                    .setSmallIcon(R.drawable.ic_notification_sync)
                    .setContentTitle("Kestrel")
                    .setOngoing(true)
                    .build()
        }

    /**
     * The first running window whose Live Update can still show owns the foreground notification; a window that is
     * about to start shows "Getting ready". Otherwise the quiet notification is used. A Live Update the user swiped
     * away, or one the engine already closed, is never brought back this way.
     */
    private fun foregroundNotification(request: SnipeRequest?): Pair<Int, Notification> {
        val registry = container.jobRegistry
        val notifications = container.notifications
        val starting = request?.takeIf { it.action.opensWindow && JobKind.WINDOW !in registry.runningKinds(it.snipeId) }
        for (window in registry.runningWindows() + listOfNotNull(starting)) {
            val live = notifications.foregroundWindowNotification(window) ?: continue
            return NotificationIds.window(window.snipeId) to live
        }
        val requests = registry.runningRequests().ifEmpty { listOfNotNull(request) }
        return NotificationIds.SERVICE to notifications.serviceNotification(requests)
    }

    private fun onJobsChanged() {
        if (destroyed) return
        if (container.jobRegistry.isEmpty()) {
            stopWhenIdle()
            return
        }
        val (id, notification) = foregroundNotificationOrFallback(request = null)
        // A window's Live Update is kept current by the notifier; only a change of owner needs startForeground.
        if (id == foregroundNotificationId && id != NotificationIds.SERVICE) return
        try {
            ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            foregroundNotificationId = id
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not switch the foreground notification", error)
        }
    }

    /** Stops only if no start arrived since, so a request Android has queued is never dropped. */
    private fun stopWhenIdle() {
        if (!destroyed && container.jobRegistry.isEmpty()) stopSelfResult(lastStartId)
    }

    companion object {
        private const val TAG = "SnipeService"

        internal fun intent(
            context: Context,
            request: SnipeRequest,
        ): Intent = request.putInto(Intent(context, SnipeService::class.java))

        /** Starts a job now, for example a dry run the user asked for. Call while the app is on screen. */
        fun start(
            context: Context,
            action: SnipeAction,
            snipe: Snipe,
        ) {
            ContextCompat.startForegroundService(context, intent(context, SnipeRequest.of(action, snipe)))
        }
    }
}
