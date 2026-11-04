package app.kestrel.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** The four channels. Creating them again is harmless: Android keeps the user's own choices. */
object NotificationChannels {
    /** The final-window Live Update. DEFAULT because promoted notifications cannot use MIN; silent by design. */
    const val LIVE = "live"

    /** Things only the user can fix: price over max, sign-in, checks, verification. */
    const val ATTENTION = "attention"

    /** Won or lost. */
    const val RESULTS = "results"

    /**
     * The service notification while a check or a dry run runs. Android raises a foreground-service notification on a
     * MIN channel to LOW the first time it is used, so it may show as LOW in settings.
     */
    const val BACKGROUND = "background"

    /** Channels whose loss would hide something the user must see; Readiness checks these. */
    val essential: List<String> = listOf(ATTENTION, RESULTS, LIVE)

    private val names =
        mapOf(
            LIVE to "Final countdown",
            ATTENTION to "Needs you",
            RESULTS to "Results",
            BACKGROUND to "Background checks",
        )

    fun displayName(channelId: String): String = names[channelId] ?: channelId

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                channel(
                    LIVE,
                    NotificationManager.IMPORTANCE_DEFAULT,
                    "The countdown and progress while Kestrel bids.",
                    silent = true,
                ),
                channel(
                    ATTENTION,
                    NotificationManager.IMPORTANCE_HIGH,
                    "Price passed your max, sign-in needed and other things only you can fix.",
                ),
                channel(RESULTS, NotificationManager.IMPORTANCE_DEFAULT, "Won or lost, when each auction ends."),
                channel(
                    BACKGROUND,
                    NotificationManager.IMPORTANCE_MIN,
                    "Shown briefly while Kestrel checks a listing.",
                    silent = true,
                ),
            ),
        )
    }

    private fun channel(
        id: String,
        importance: Int,
        description: String,
        silent: Boolean = false,
    ) = NotificationChannel(id, displayName(id), importance).apply {
        this.description = description
        if (silent) {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
    }
}
