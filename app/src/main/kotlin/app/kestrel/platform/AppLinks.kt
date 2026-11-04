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
    const val EXTRA_EDIT_MAX = "app.kestrel.extra.EDIT_MAX"

    /** [editMax] opens the snipe with its edit sheet; its own address keeps it apart from the plain link. */
    fun openSnipe(
        context: Context,
        snipeId: Long,
        editMax: Boolean = false,
    ): Intent =
        Intent(
            Intent.ACTION_VIEW,
            (if (editMax) "kestrel://snipe/$snipeId/edit-max" else "kestrel://snipe/$snipeId").toUri(),
            context,
            MainActivity::class.java,
        ).putExtra(EXTRA_SNIPE_ID, snipeId)
            .putExtra(EXTRA_EDIT_MAX, editMax)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** The snipe a notification asked to open, or null for a normal launch. */
    fun snipeIdFrom(intent: Intent?): Long? = intent?.getLongExtra(EXTRA_SNIPE_ID, 0L)?.takeIf { it > 0L }

    /** True when the link asks for the snipe's edit sheet. */
    fun editMaxFrom(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_EDIT_MAX, false) == true

    const val EXTRA_HAND_OFF_ID = "app.kestrel.extra.HAND_OFF_SNIPE_ID"

    /**
     * The snipe's listing in Kestrel's visible browser, where the user answers what eBay asks (DESIGN 5.4 "Open eBay
     * page"). MainActivity never shows over the lock screen, so the phone is unlocked first.
     */
    fun openHandOff(
        context: Context,
        snipeId: Long,
    ): Intent =
        Intent(Intent.ACTION_VIEW, "kestrel://snipe/$snipeId/hand-off".toUri(), context, MainActivity::class.java)
            .putExtra(EXTRA_HAND_OFF_ID, snipeId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** The snipe whose listing a notification asked to hand to the user, or null. */
    fun handOffIdFrom(intent: Intent?): Long? = intent?.getLongExtra(EXTRA_HAND_OFF_ID, 0L)?.takeIf { it > 0L }
}

/** Broadcasts from notification buttons to [NotificationActionReceiver]. */
internal object NotificationActions {
    const val ACTION_RAISE_MAX = "app.kestrel.action.RAISE_MAX"
    const val ACTION_STOP_SNIPING = "app.kestrel.action.STOP_SNIPING"
    const val ACTION_UNDO_CANCEL = "app.kestrel.action.UNDO_CANCEL"
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

    fun windowDismissed(
        context: Context,
        snipeId: Long,
    ): Intent = intent(context, ACTION_WINDOW_DISMISSED, snipeId)

    fun stopSniping(
        context: Context,
        snipeId: Long,
    ): Intent = intent(context, ACTION_STOP_SNIPING, snipeId)

    fun undoCancel(
        context: Context,
        snipeId: Long,
    ): Intent = intent(context, ACTION_UNDO_CANCEL, snipeId)

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
