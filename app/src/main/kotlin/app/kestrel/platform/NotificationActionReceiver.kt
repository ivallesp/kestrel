package app.kestrel.platform

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.kestrel.data.EditResult
import app.kestrel.data.SnipeRepository
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import kotlinx.coroutines.launch

/** Handles the buttons on Kestrel's notifications: Raise max (typed reply), Cancel snipe, and a dismissed Live Update. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val container = context.kestrelContainer
        val pending: PendingResult? = goAsync()
        container.applicationScope.launch {
            try {
                container.notificationActions.handle(intent)
            } catch (error: Exception) {
                Log.e(TAG, "Notification action ${intent.action} failed", error)
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        const val TAG = "NotificationAction"
    }
}

internal class NotificationActionHandler(
    private val repository: SnipeRepository,
    private val notifications: SnipeNotifications,
    private val jobs: SnipeJobRegistry,
    private val rules: RaiseMaxRules,
    private val reschedule: (Snipe) -> Unit,
    private val cancelSchedule: (Long) -> Unit,
    private val minimumNextBid: (Snipe) -> Money?,
) {
    suspend fun handle(intent: Intent) {
        val snipeId = intent.getLongExtra(NotificationActions.EXTRA_SNIPE_ID, 0L)
        if (snipeId <= 0L) return
        when (intent.action) {
            NotificationActions.ACTION_RAISE_MAX -> raiseMax(snipeId, intent)
            NotificationActions.ACTION_CANCEL_SNIPE -> cancelSnipe(snipeId)
            NotificationActions.ACTION_WINDOW_DISMISSED -> notifications.onWindowDismissed(snipeId)
        }
    }

    private suspend fun raiseMax(
        snipeId: Long,
        intent: Intent,
    ) {
        val typed =
            RemoteInput
                .getResultsFromIntent(intent)
                ?.getCharSequence(NotificationActions.KEY_NEW_MAX)
                ?.toString()
        val snipe = repository.snipe(snipeId)
        if (snipe == null || snipe.status.isFinal) {
            notifications.cancelPriceAlert(snipeId)
            return
        }
        val currency =
            intent.getStringExtra(NotificationActions.EXTRA_CURRENCY) ?: snipe.maxBid?.currency
                ?: snipe.ref.site.currency
        val alertPrice = intent.minorExtra(NotificationActions.EXTRA_CURRENT_MINOR)?.let { Money(it, currency) }
        val alertMinimum = intent.minorExtra(NotificationActions.EXTRA_MINIMUM_MINOR)?.let { Money(it, currency) }
        // The price may have moved since the alert was posted; judge the reply against the newest figures known.
        val current = highest(currency, snipe.lastPrice, alertPrice)
        val minimum = highest(currency, minimumFor(snipe), alertMinimum)
        when (val decision = rules.decide(typed, currency, snipe.maxBid, current, minimum)) {
            is RaiseMaxDecision.Reject -> notifications.showPriceAlert(snipe, current, minimum, decision.message)
            is RaiseMaxDecision.Accept -> applyNewMax(snipe, decision.newMax, current, minimum)
        }
    }

    private suspend fun applyNewMax(
        snipe: Snipe,
        newMax: Money,
        current: Money?,
        minimum: Money?,
    ) {
        when (val result = repository.updateMax(snipe.id, newMax)) {
            is EditResult.Done -> {
                reschedule(result.snipe)
                notifications.showRaiseMaxDone(result.snipe, newMax)
            }

            is EditResult.NotAllowed -> {
                notifications.showPriceAlert(snipe, current, minimum, result.reason)
            }

            EditResult.NotFound -> {
                notifications.cancelPriceAlert(snipe.id)
            }
        }
    }

    private suspend fun cancelSnipe(snipeId: Long) {
        when (val result = repository.cancel(snipeId)) {
            is EditResult.Done -> {
                // Stored first, so an engine that re-reads the snipe already sees it cancelled.
                jobs.cancelAll(snipeId)
                cancelSchedule(snipeId)
                notifications.cancelForSnipe(snipeId)
            }

            is EditResult.NotAllowed -> {
                repository.snipe(snipeId)?.let { notifications.showCancelRefused(it, result.reason) }
            }

            EditResult.NotFound -> {
                cancelSchedule(snipeId)
                notifications.cancelForSnipe(snipeId)
            }
        }
    }

    private fun minimumFor(snipe: Snipe): Money? =
        try {
            minimumNextBid(snipe)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not work out eBay's minimum for snipe ${snipe.id}", error)
            null
        }

    private fun highest(
        currency: String,
        vararg amounts: Money?,
    ): Money? = amounts.filterNotNull().filter { it.currency == currency }.maxOrNull()

    private fun Intent.minorExtra(key: String): Long? = if (hasExtra(key)) getLongExtra(key, 0L) else null

    private companion object {
        const val TAG = "NotificationAction"
    }
}
