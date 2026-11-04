package app.kestrel.platform

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.kestrel.model.Money
import app.kestrel.ui.MainActivity

/**
 * The intents notifications use to open the app at one snipe. MainActivity (singleTask) receives them in onCreate or
 * onNewIntent; [snipeIdFrom] reads the snipe back out.
 */
object AppLinks {
    const val EXTRA_SNIPE_ID = "app.kestrel.extra.OPEN_SNIPE_ID"

    fun openSnipe(
        context: Context,
        snipeId: Long,
    ): Intent =
        Intent(Intent.ACTION_VIEW, "kestrel://snipe/$snipeId".toUri(), context, MainActivity::class.java)
            .putExtra(EXTRA_SNIPE_ID, snipeId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** The snipe a notification asked to open, or null for a normal launch. */
    fun snipeIdFrom(intent: Intent?): Long? = intent?.getLongExtra(EXTRA_SNIPE_ID, 0L)?.takeIf { it > 0L }
}

/** Broadcasts from notification buttons to [NotificationActionReceiver]. */
internal object NotificationActions {
    const val ACTION_RAISE_MAX = "app.kestrel.action.RAISE_MAX"
    const val ACTION_CANCEL_SNIPE = "app.kestrel.action.CANCEL_SNIPE"
    const val ACTION_WINDOW_DISMISSED = "app.kestrel.action.WINDOW_DISMISSED"

    /** The RemoteInput key of the typed new max. */
    const val KEY_NEW_MAX = "new_max"

    const val EXTRA_SNIPE_ID = "app.kestrel.extra.SNIPE_ID"
    const val EXTRA_CURRENCY = "app.kestrel.extra.CURRENCY"
    const val EXTRA_CURRENT_MINOR = "app.kestrel.extra.CURRENT_MINOR"
    const val EXTRA_MINIMUM_MINOR = "app.kestrel.extra.MINIMUM_MINOR"

    fun raiseMax(
        context: Context,
        snipeId: Long,
        currency: String,
        current: Money?,
        minimumNext: Money?,
    ): Intent =
        intent(context, ACTION_RAISE_MAX, snipeId)
            .putExtra(EXTRA_CURRENCY, currency)
            .apply {
                current?.takeIf { it.currency == currency }?.let { putExtra(EXTRA_CURRENT_MINOR, it.minor) }
                minimumNext?.takeIf { it.currency == currency }?.let { putExtra(EXTRA_MINIMUM_MINOR, it.minor) }
            }

    fun cancelSnipe(
        context: Context,
        snipeId: Long,
    ): Intent = intent(context, ACTION_CANCEL_SNIPE, snipeId)

    fun windowDismissed(
        context: Context,
        snipeId: Long,
    ): Intent = intent(context, ACTION_WINDOW_DISMISSED, snipeId)

    private fun intent(
        context: Context,
        action: String,
        snipeId: Long,
    ): Intent =
        Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData("kestrel://${action.substringAfterLast('.').lowercase()}/$snipeId".toUri())
            .putExtra(EXTRA_SNIPE_ID, snipeId)
}
