package app.kestrel.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import app.kestrel.model.Snipe

/** Why a snipe could not get the alarms it needs. Shown to the user, because a late alarm can miss the auction. */
enum class ScheduleProblem {
    /** Android refused exact alarms; Kestrel fell back to an inexact alarm that may fire late. */
    EXACT_ALARMS_DENIED,

    /** Android's limit on pending alarms per app was reached. */
    ALARM_LIMIT_REACHED,

    /** WorkManager refused the background check. */
    BACKGROUND_CHECK_FAILED,
}

/** The system state that decides which alarm API to use. */
internal interface AlarmPermissions {
    fun canScheduleExactAlarms(): Boolean

    /** True when battery use is Unrestricted, which exempts every alarm from Doze limits. */
    fun isIgnoringBatteryOptimizations(): Boolean
}

internal class AndroidAlarmPermissions(
    private val context: Context,
) : AlarmPermissions {
    override fun canScheduleExactAlarms(): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    override fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
}

/** Told when scheduling fails or recovers, so the user hears about it. */
internal interface ScheduleProblemNotifier {
    fun showScheduleProblem(problem: ScheduleProblem)

    fun clearScheduleProblem()
}

/** The PendingIntents behind each alarm: one per (snipe, action), told apart by request code and data URI. */
internal object AlarmIntents {
    fun pendingIntent(
        context: Context,
        action: SnipeAction,
        snipe: Snipe,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(snipe.id, action),
            intent(context, SnipeRequest.of(action, snipe)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The live PendingIntent for this alarm, or null when none was ever set (so there is nothing to cancel). */
    fun existing(
        context: Context,
        action: SnipeAction,
        snipeId: Long,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode(snipeId, action),
            intent(context, SnipeRequest(action, snipeId, title = null, endTimeUtcMillis = null)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )

    fun intent(
        context: Context,
        request: SnipeRequest,
    ): Intent = request.putInto(Intent(context, AlarmReceiver::class.java))

    private fun requestCode(
        snipeId: Long,
        action: SnipeAction,
    ): Int =
        NotificationIds.requestCode(
            snipeId,
            when (action) {
                SnipeAction.CHECK -> NotificationIds.Slot.ALARM_CHECK
                SnipeAction.DRY_RUN -> NotificationIds.Slot.ALARM_DRY_RUN
                SnipeAction.WINDOW -> NotificationIds.Slot.ALARM_WINDOW
                SnipeAction.WINDOW_BACKUP -> NotificationIds.Slot.ALARM_WINDOW_BACKUP
            },
        )
}
