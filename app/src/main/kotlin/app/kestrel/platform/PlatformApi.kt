package app.kestrel.platform

import android.content.Context
import app.kestrel.model.Snipe

// Public API of the platform layer. Owned by the platform-data lane; bodies are stubs until it lands.

/** Schedules every alarm and job a snipe needs (checks, dry run, window, backup) and cancels them. */
class SnipeScheduler(
    private val context: Context,
) {
    fun schedule(snipe: Snipe): Unit = TODO("platform-data lane")

    fun cancel(snipeId: Long): Unit = TODO("platform-data lane")

    /** Re-arms everything from the database (boot, update, time change). */
    suspend fun rescheduleAll(): Unit = TODO("platform-data lane")
}

/** One item of the "Ready to snipe" checklist. */
data class ReadinessItem(
    val key: ReadinessKey,
    val ok: Boolean,
    val detail: String,
)

enum class ReadinessKey { EBAY_SESSION, NOTIFICATIONS, EXACT_ALARMS, BATTERY, LIVE_UPDATES, CLOCK, DRY_RUN, NETWORK }

class Readiness(
    private val context: Context,
) {
    suspend fun check(): List<ReadinessItem> = TODO("platform-data lane")
}
