package app.kestrel.platform

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import app.kestrel.model.Snipe
import app.kestrel.model.StatusFamily

/** The last time Android ended Kestrel's process, and why. */
internal data class ProcessExit(
    val atUtcMillis: Long,
    val description: String,
)

/**
 * When the process starts after Android ended it, each snipe whose bid window it cut off gets a timeline line that
 * says why (the memory limiter, a crash, a stop from Active apps). Without it the user only sees that no bid went out.
 * A window was cut off when its snipe is still in a bidding status without a result and was last written before the
 * exit. Each exit is noted once.
 */
internal class LastExitNote(
    private val lastExit: () -> ProcessExit?,
    private val pendingSnipes: suspend () -> List<Snipe>,
    private val addEvent: suspend (snipeId: Long, message: String) -> Unit,
    private val alreadyNoted: () -> Long,
    private val markNoted: (Long) -> Unit,
) {
    suspend fun noteIfNew() {
        val exit = lastExit() ?: return
        if (exit.atUtcMillis <= alreadyNoted()) return
        val cutOff =
            pendingSnipes().filter {
                it.status.family == StatusFamily.BIDDING && it.result == null &&
                    it.updatedAtUtcMillis <= exit.atUtcMillis
            }
        for (snipe in cutOff) {
            addEvent(snipe.id, "Android ended Kestrel while the bid window ran (${exit.description}).")
        }
        markNoted(exit.atUtcMillis)
    }

    companion object {
        /** The most recent exit of an earlier process of this app, from ActivityManager. */
        fun lastExitOf(context: Context): ProcessExit? {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return null
            val info = manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return null
            return ProcessExit(info.timestamp, describe(info))
        }

        private fun describe(info: ApplicationExitInfo): String {
            val reason =
                when (info.reason) {
                    ApplicationExitInfo.REASON_LOW_MEMORY -> {
                        "Android ended it to free memory"
                    }

                    ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE -> {
                        "it crashed"
                    }

                    ApplicationExitInfo.REASON_ANR -> {
                        "it stopped responding"
                    }

                    ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_USER_STOPPED -> {
                        "it was stopped from the phone's settings or Active apps"
                    }

                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> {
                        "Android ended it for using too much"
                    }

                    ApplicationExitInfo.REASON_FREEZER -> {
                        "Android ended it while it was frozen"
                    }

                    else -> {
                        "exit reason ${info.reason}"
                    }
                }
            return info.description?.takeIf { it.isNotBlank() }?.let { "$reason: $it" } ?: reason
        }
    }
}
