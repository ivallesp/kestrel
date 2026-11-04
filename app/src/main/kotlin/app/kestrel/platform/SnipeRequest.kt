package app.kestrel.platform

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.kestrel.AppContainer
import app.kestrel.KestrelApp
import app.kestrel.model.Snipe

/** What an alarm, a notification tap or the UI asks the snipe service to do. */
enum class SnipeAction {
    /** Reload the listing and update price and end time. */
    CHECK,

    /** About ten minutes before the end: open the bid layer and type the max, never submit. */
    DRY_RUN,

    /** The final window: bid at the set second and follow the auction to its result. */
    WINDOW,

    /** A second wake-up for the same window; does nothing if the window is already running. */
    WINDOW_BACKUP,
    ;

    val opensWindow: Boolean get() = this == WINDOW || this == WINDOW_BACKUP
}

/** One request for one snipe. [title] and [endTimeUtcMillis] are only for notifications shown before the snipe is read. */
internal data class SnipeRequest(
    val action: SnipeAction,
    val snipeId: Long,
    val title: String?,
    val endTimeUtcMillis: Long?,
) {
    fun putInto(intent: Intent): Intent =
        intent
            .setAction(ACTION_PREFIX + action.name)
            .setData("kestrel://${action.name.lowercase()}/$snipeId".toUri())
            .putExtra(EXTRA_SNIPE_ID, snipeId)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_END_UTC_MILLIS, endTimeUtcMillis ?: NO_TIME)

    companion object {
        private const val ACTION_PREFIX = "app.kestrel.action."
        private const val EXTRA_SNIPE_ID = "app.kestrel.extra.SNIPE_ID"
        private const val EXTRA_TITLE = "app.kestrel.extra.TITLE"
        private const val EXTRA_END_UTC_MILLIS = "app.kestrel.extra.END_UTC_MILLIS"
        private const val NO_TIME = -1L

        fun of(
            action: SnipeAction,
            snipe: Snipe,
        ) = SnipeRequest(action, snipe.id, snipe.title, snipe.endTimeUtcMillis)

        /** Null for anything that is not a well-formed request, so a stray intent can never start a job. */
        fun from(intent: Intent): SnipeRequest? {
            val name = intent.action?.removePrefix(ACTION_PREFIX) ?: return null
            val action = SnipeAction.entries.firstOrNull { it.name == name } ?: return null
            val snipeId = intent.getLongExtra(EXTRA_SNIPE_ID, 0L)
            if (snipeId <= 0L) return null
            val end = intent.getLongExtra(EXTRA_END_UTC_MILLIS, NO_TIME).takeIf { it != NO_TIME }
            return SnipeRequest(action, snipeId, intent.getStringExtra(EXTRA_TITLE), end)
        }
    }
}

internal val Context.kestrelContainer: AppContainer
    get() = (applicationContext as KestrelApp).container
