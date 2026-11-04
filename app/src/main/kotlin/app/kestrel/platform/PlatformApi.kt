package app.kestrel.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import app.kestrel.AppContainer
import app.kestrel.engine.EngineClock
import app.kestrel.model.Snipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Public API of the platform layer.

/**
 * Schedules every alarm and job a snipe needs (checks, dry run, window, backup) and cancels them.
 *
 * Exact alarms use setExactAndAllowWhileIdle on ELAPSED_REALTIME_WAKEUP, computed from true time, so a wrong phone
 * clock cannot move them. Without Unrestricted battery the dry run and both window alarms use setAlarmClock instead,
 * the one alarm Android never defers. Checks more than an hour before the end go to WorkManager.
 */
class SnipeScheduler internal constructor(
    private val context: Context,
    private val clock: EngineClock,
    private val systemUtcMillis: () -> Long,
    private val planner: SchedulePlanner,
    private val pendingSnipes: suspend () -> List<Snipe>,
    private val permissions: AlarmPermissions,
    private val workManager: () -> WorkManager,
    private val problemNotifier: ScheduleProblemNotifier,
    private val isWindowRunning: (snipeId: Long) -> Boolean,
) {
    constructor(context: Context) : this(context, context.kestrelContainer)

    private constructor(context: Context, container: AppContainer) : this(
        context = context,
        clock = container.clock,
        systemUtcMillis = System::currentTimeMillis,
        planner = SchedulePlanner(container.schedulePolicy),
        pendingSnipes = container.repository::pendingSnipes,
        permissions = AndroidAlarmPermissions(context),
        workManager = { WorkManager.getInstance(context) },
        problemNotifier = container.notifications,
        isWindowRunning = container::isWindowRunning,
    )

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)
    private val currentProblem = MutableStateFlow<ScheduleProblem?>(null)

    /** The last scheduling failure, or null once scheduling works again. The UI can show it as a banner. */
    val problem: StateFlow<ScheduleProblem?> = currentProblem.asStateFlow()

    fun schedule(snipe: Snipe): Unit = schedule(snipe, lastCheckAttemptUtcMillis = null)

    /** [lastCheckAttemptUtcMillis]: when a check just ran (even if it failed), so the next one waits its interval. */
    internal fun schedule(
        snipe: Snipe,
        lastCheckAttemptUtcMillis: Long?,
    ) {
        val now = clock.nowUtcMillis()
        val plan = planner.plan(snipe, now, lastCheckAttemptUtcMillis, windowRunning = isWindowRunning(snipe.id))
        if (plan.isEmpty) {
            cancel(snipe.id)
            return
        }
        val problems = mutableSetOf<ScheduleProblem>()
        setOrCancelAlarm(SnipeAction.DRY_RUN, snipe, plan.dryRunAtUtcMillis, now, problems)
        setOrCancelAlarm(SnipeAction.WINDOW, snipe, plan.windowAtUtcMillis, now, problems)
        setOrCancelAlarm(SnipeAction.WINDOW_BACKUP, snipe, plan.backupAtUtcMillis, now, problems)
        val check = plan.check
        when {
            check == null -> {
                cancelAlarm(SnipeAction.CHECK, snipe.id)
                cancelCheckWork(snipe.id)
            }

            check.deferrable -> {
                cancelAlarm(SnipeAction.CHECK, snipe.id)
                enqueueCheckWork(snipe.id, check.atUtcMillis - now, problems)
            }

            else -> {
                cancelCheckWork(snipe.id)
                setAlarm(SnipeAction.CHECK, snipe, check.atUtcMillis, now, problems)
            }
        }
        report(problems)
    }

    fun cancel(snipeId: Long) {
        SnipeAction.entries.forEach { cancelAlarm(it, snipeId) }
        cancelCheckWork(snipeId)
    }

    /** Re-arms everything from the database (boot, update, time change). One bad snipe never stops the rest. */
    suspend fun rescheduleAll() {
        for (snipe in pendingSnipes()) {
            // Lets a caller with a time limit stop between snipes; scheduling one snipe is quick.
            currentCoroutineContext().ensureActive()
            try {
                schedule(snipe)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Could not reschedule snipe ${snipe.id}", error)
            }
        }
    }

    private fun setOrCancelAlarm(
        action: SnipeAction,
        snipe: Snipe,
        atUtcMillis: Long?,
        now: Long,
        problems: MutableSet<ScheduleProblem>,
    ) {
        if (atUtcMillis == null) cancelAlarm(action, snipe.id) else setAlarm(action, snipe, atUtcMillis, now, problems)
    }

    private fun setAlarm(
        action: SnipeAction,
        snipe: Snipe,
        atUtcMillis: Long,
        now: Long,
        problems: MutableSet<ScheduleProblem>,
    ) {
        val manager = alarmManager ?: return
        val operation = AlarmIntents.pendingIntent(context, action, snipe)
        val delay = (atUtcMillis - now).coerceAtLeast(0L)
        val elapsedTarget = clock.elapsedRealtimeMillis() + delay
        try {
            if (!permissions.canScheduleExactAlarms()) {
                problems += ScheduleProblem.EXACT_ALARMS_DENIED
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTarget, operation)
            } else if (action != SnipeAction.CHECK && !permissions.isIgnoringBatteryOptimizations()) {
                // An alarm clock is RTC, so it is placed on the phone's own clock at the same true moment.
                val showIntent = alarmClockShowIntent(snipe.id)
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(systemUtcMillis() + delay, showIntent), operation)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTarget, operation)
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "Exact alarm refused for snipe ${snipe.id}", error)
            problems += ScheduleProblem.EXACT_ALARMS_DENIED
            setInexactFallback(manager, elapsedTarget, operation, problems)
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Alarm limit reached for snipe ${snipe.id}", error)
            problems += ScheduleProblem.ALARM_LIMIT_REACHED
        }
    }

    /** Late is better than never: an inexact alarm at least reminds the user to tap. */
    private fun setInexactFallback(
        manager: AlarmManager,
        elapsedTarget: Long,
        operation: PendingIntent,
        problems: MutableSet<ScheduleProblem>,
    ) {
        try {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTarget, operation)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Even an inexact alarm was refused", error)
            problems += ScheduleProblem.ALARM_LIMIT_REACHED
        }
    }

    private fun cancelAlarm(
        action: SnipeAction,
        snipeId: Long,
    ) {
        val existing = AlarmIntents.existing(context, action, snipeId) ?: return
        alarmManager?.cancel(existing)
        existing.cancel()
    }

    private fun enqueueCheckWork(
        snipeId: Long,
        delayMillis: Long,
        problems: MutableSet<ScheduleProblem>,
    ) {
        try {
            workManager().enqueueUniqueWork(
                CheckWorker.uniqueName(snipeId),
                ExistingWorkPolicy.REPLACE,
                CheckWorker.request(snipeId, delayMillis),
            )
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not enqueue the background check for snipe $snipeId", error)
            problems += ScheduleProblem.BACKGROUND_CHECK_FAILED
        }
    }

    private fun cancelCheckWork(snipeId: Long) {
        try {
            workManager().cancelUniqueWork(CheckWorker.uniqueName(snipeId))
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not cancel the background check for snipe $snipeId", error)
        }
    }

    private fun alarmClockShowIntent(snipeId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            NotificationIds.requestCode(snipeId, NotificationIds.Slot.ALARM_CLOCK_SHOW),
            AppLinks.openSnipe(context, snipeId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun report(problems: Set<ScheduleProblem>) {
        val worst = problems.minByOrNull { it.ordinal }
        val previous = currentProblem.value
        currentProblem.value = worst
        when {
            worst != null && worst != previous -> problemNotifier.showScheduleProblem(worst)
            worst == null && previous != null -> problemNotifier.clearScheduleProblem()
        }
    }

    private companion object {
        const val TAG = "SnipeScheduler"
    }
}

/** One item of the "Ready to snipe" checklist. */
data class ReadinessItem(
    val key: ReadinessKey,
    val ok: Boolean,
    val detail: String,
)

enum class ReadinessKey {
    EBAY_SESSION,
    NOTIFICATIONS,
    EXACT_ALARMS,
    BATTERY,
    LIVE_UPDATES,
    CLOCK,
    DRY_RUN,
    NETWORK,

    /** Standby bucket and background restriction. */
    BACKGROUND,

    /** "Pause app activity if unused" (app hibernation). */
    UNUSED_APP_PAUSE,
}

class Readiness internal constructor(
    private val context: Context,
    private val probes: ReadinessProbes,
    private val dayAndTime: (Long) -> String,
) {
    constructor(context: Context) : this(context, context.kestrelContainer)

    private constructor(context: Context, container: AppContainer) : this(
        context = context,
        probes = container.readinessProbes,
        dayAndTime = container.notificationTexts::dayAndTime,
    )

    /** Every item, in a fixed order; the UI puts the ones that are not ok first. */
    suspend fun check(): List<ReadinessItem> = ReadinessEvaluator(probes, dayAndTime).evaluate()

    /** The settings screen that fixes [key], or null when the fix is in the app itself (sign-in, dry run). */
    fun fixIntent(key: ReadinessKey): Intent? = ReadinessFixes(context, probes).intent(key)
}
