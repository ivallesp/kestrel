package app.kestrel.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import app.kestrel.data.ArmedSnipesCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Re-arms everything after a reboot, an app update or a clock or time-zone change. Before the first unlock after a
 * reboot only device-protected storage is readable, so then it just tells the user how many snipes wait.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            try {
                if (!isUserUnlocked(context)) context.kestrelContainer.lockedBootNotice.post()
            } catch (error: RuntimeException) {
                Log.e(TAG, "Could not post the unlock notice", error)
            }
            return
        }
        if (action !in RESCHEDULE_ACTIONS) return
        val container = context.kestrelContainer
        val pending: PendingResult? = goAsync()
        container.applicationScope.launch {
            try {
                container.systemEvents.onRescheduleEvent(action)
            } catch (error: Exception) {
                Log.e(TAG, "Rescheduling after $action failed", error)
            } finally {
                pending?.finish()
            }
        }
    }

    private fun isUserUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked == true

    companion object {
        private const val TAG = "SystemEventReceiver"

        val RESCHEDULE_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}

/** What a reschedule event does, apart from the receiver plumbing. */
internal class SystemEventHandler(
    private val rescheduleAll: suspend () -> Unit,
    private val purgeDeletedBefore: suspend (Long) -> Unit,
    private val cancelUnlockNotice: () -> Unit,
    private val nowUtcMillis: () -> Long,
) {
    suspend fun onRescheduleEvent(action: String) {
        if (action == Intent.ACTION_BOOT_COMPLETED) cancelUnlockNotice()
        try {
            rescheduleAll()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Could not reschedule after $action", error)
        }
        try {
            purgeDeletedBefore(nowUtcMillis() - DELETED_KEPT_MILLIS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not purge deleted snipes", error)
        }
    }

    private companion object {
        const val TAG = "SystemEventHandler"
        const val DELETED_KEPT_MILLIS = 24 * 60 * 60 * 1000L
    }
}

/** The "Unlock your phone" notice, built from the device-protected copy only. */
internal class LockedBootNotice(
    private val armedSnipes: ArmedSnipesCopy,
    private val notifications: SnipeNotifications,
    private val systemUtcMillis: () -> Long,
) {
    fun post() {
        val now = systemUtcMillis()
        val upcoming = armedSnipes.read().filter { it.endTimeUtcMillis > now }.sortedBy { it.endTimeUtcMillis }
        if (upcoming.isEmpty()) return
        notifications.showUnlockToArm(count = upcoming.size, nextEndUtcMillis = upcoming.first().endTimeUtcMillis)
    }
}
