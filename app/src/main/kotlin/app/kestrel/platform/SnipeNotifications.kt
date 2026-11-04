package app.kestrel.platform

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.Settings
import android.util.Log
import app.kestrel.R
import app.kestrel.data.SnipeRules
import app.kestrel.engine.EngineClock
import app.kestrel.engine.NeedsUserReason
import app.kestrel.engine.SnipeNotifier
import app.kestrel.engine.WindowPhase
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * All of Kestrel's notifications. The final window is one Live Update per snipe (promoted, ongoing, with a
 * countdown chip and phase points); everything else is a normal notification on the matching channel.
 *
 * The engine calls the [SnipeNotifier] methods; the service and receivers call the rest.
 */
class SnipeNotifications internal constructor(
    private val context: Context,
    private val clock: EngineClock,
    private val systemUtcMillis: () -> Long,
    private val texts: NotificationTexts,
    private val photos: ItemPhotos,
    private val scope: CoroutineScope,
    private val canUseFullScreenIntent: () -> Boolean,
) : SnipeNotifier,
    ScheduleProblemNotifier {
    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)

    /** What the window Live Update of each snipe currently shows; guarded by itself. */
    private val windows = HashMap<Long, WindowState>()

    /** Windows the engine closed; their job may still be finishing, but their Live Update must not return. */
    private val closedWindows = HashSet<Long>()

    private class WindowState(
        var snipe: Snipe?,
        val snipeId: Long,
        var title: String?,
        var endTimeUtcMillis: Long?,
        var bidAtUtcMillis: Long? = null,
        var phase: WindowPhase? = null,
        var detail: String? = null,
        var outcome: SnipeOutcome? = null,
        var dismissed: Boolean = false,
        var latest: Notification? = null,
    )

    fun createChannels() = NotificationChannels.create(context)

    // The final window.

    /**
     * The first Live Update of a window, shown by the service the moment it starts, before the snipe is read.
     * Later [windowOpened] and [windowProgress] calls replace it in place.
     */
    internal fun preparingWindowNotification(request: SnipeRequest): Notification =
        synchronized(windows) {
            closedWindows.remove(request.snipeId)
            val state =
                windows.getOrPut(request.snipeId) {
                    WindowState(
                        snipe = null,
                        snipeId = request.snipeId,
                        title = request.title,
                        endTimeUtcMillis = request.endTimeUtcMillis,
                    )
                }
            buildWindow(state).also { state.latest = it }
        }

    /**
     * What the service shows as its foreground notification for a running window: its latest Live Update, or
     * "Getting ready" if the engine has not opened it yet. Null once the engine closed it or the user swiped it
     * away, so the service never brings it back.
     */
    internal fun foregroundWindowNotification(request: SnipeRequest): Notification? {
        synchronized(windows) {
            val state = windows[request.snipeId]
            if (state != null) return state.takeUnless { it.dismissed }?.latest
            if (request.snipeId in closedWindows) return null
        }
        return preparingWindowNotification(request)
    }

    override fun windowOpened(
        snipe: Snipe,
        bidAtUtcMillis: Long,
    ) {
        updateWindow(snipe) {
            it.bidAtUtcMillis = bidAtUtcMillis
            it.phase = WindowPhase.CHECKING
            it.detail = null
            it.outcome = null
        }
        loadPhoto(snipe) { refreshWindow(snipe.id) }
    }

    override fun windowProgress(
        snipe: Snipe,
        phase: WindowPhase,
        detail: String?,
    ) {
        updateWindow(snipe) {
            it.phase = phase
            it.detail = detail
        }
    }

    override fun windowClosed(snipeId: Long) {
        synchronized(windows) {
            windows.remove(snipeId)
            closedWindows.add(snipeId)
        }
        // Ignored by Android while it is the service's foreground notification; the service drops it when it moves on.
        cancel(NotificationIds.window(snipeId))
    }

    /** The user swiped the Live Update away; it must not come back for this window. */
    internal fun onWindowDismissed(snipeId: Long) {
        synchronized(windows) { windows[snipeId]?.dismissed = true }
    }

    private fun hasWindow(snipeId: Long): Boolean = synchronized(windows) { windows.containsKey(snipeId) }

    /** Rebuilds an open window from what it already shows, for example once its photo has loaded. */
    private fun refreshWindow(snipeId: Long) {
        val notification =
            synchronized(windows) {
                val state = windows[snipeId] ?: return
                buildWindow(state).also { state.latest = it }.takeUnless { state.dismissed }
            }
        notification?.let { post(NotificationIds.window(snipeId), it) }
    }

    private fun updateWindow(
        snipe: Snipe,
        change: (WindowState) -> Unit,
    ) {
        val notification =
            synchronized(windows) {
                closedWindows.remove(snipe.id)
                val state =
                    windows.getOrPut(snipe.id) {
                        WindowState(
                            snipe = snipe,
                            snipeId = snipe.id,
                            title = snipe.title,
                            endTimeUtcMillis = snipe.endTimeUtcMillis,
                        )
                    }
                state.snipe = snipe
                state.title = snipe.title
                state.endTimeUtcMillis = snipe.endTimeUtcMillis
                change(state)
                val built = buildWindow(state)
                state.latest = built
                built.takeUnless { state.dismissed }
            }
        notification?.let { post(NotificationIds.window(snipe.id), it) }
    }

    private fun buildWindow(state: WindowState): Notification {
        val snipe = state.snipe
        val bidAt = state.bidAtUtcMillis
        val title = if (snipe == null) texts.preparingTitle() else texts.windowTitle(snipe, bidAt)
        val text =
            if (snipe == null) texts.preparingText(state.title) else texts.windowText(snipe, state.phase, state.detail)
        val dismissed = NotificationActions.windowDismissed(context, state.snipeId)
        val builder =
            Notification
                .Builder(context, NotificationChannels.LIVE)
                .setSmallIcon(R.drawable.ic_notification_gavel)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setRequestPromotedOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(windowPublicVersion(bidAt))
                .setContentIntent(openSnipe(state.snipeId))
                .setDeleteIntent(broadcast(dismissed, state.snipeId, NotificationIds.Slot.WINDOW_DISMISSED))
                .addAction(cancelAction(state.snipeId))
                .setStyle(progressStyle(state))
        state.endTimeUtcMillis?.let { end ->
            // A Live Update left behind by a killed process must not count down forever.
            val untilGone = end + WINDOW_NOTIFICATION_LINGER_MILLIS - clock.nowUtcMillis()
            builder.setTimeoutAfter(untilGone.coerceAtLeast(MINIMUM_TIMEOUT_MILLIS))
        }
        photos.cached(snipe?.imageUrl)?.let { builder.setLargeIcon(it) }
        val chip = state.outcome?.let(texts::chipText) ?: texts.chipText(state.phase)
        chip?.let { builder.setShortCriticalText(it) }
        if (bidAt != null && state.outcome == null) {
            builder
                .setWhen(toDeviceClock(bidAt))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    private fun progressStyle(state: WindowState): Notification.ProgressStyle {
        val segments = WindowProgress.segmentLengths.map { Notification.ProgressStyle.Segment(it) }
        val points = WindowProgress.points.map { Notification.ProgressStyle.Point(it.position).setId(it.ordinal) }
        val style =
            Notification
                .ProgressStyle()
                .setStyledByProgress(true)
                .setProgressSegments(segments)
                .setProgressPoints(points)
                .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_notification_gavel))
        val progress = WindowProgress.progressFor(state.phase, state.outcome)
        if (progress == null) style.setProgressIndeterminate(true) else style.setProgress(progress)
        return style
    }

    private fun windowPublicVersion(bidAtUtcMillis: Long?): Notification =
        Notification
            .Builder(context, NotificationChannels.LIVE)
            .setSmallIcon(R.drawable.ic_notification_gavel)
            .setContentTitle("Kestrel")
            .setContentText(texts.windowPublicText(bidAtUtcMillis))
            .build()

    // Alerts.

    override fun priceOverMax(
        snipe: Snipe,
        current: Money,
        minimumNext: Money?,
    ) {
        showPriceAlert(snipe, current, minimumNext, problem = null)
    }

    /**
     * The price alert; [problem] explains why a typed max was refused (re-posting also clears the reply field). The
     * Raise max reply is offered only while the store would still take a new max (SnipeRules.maxOrLeadRefusal).
     */
    internal fun showPriceAlert(
        snipe: Snipe,
        current: Money?,
        minimumNext: Money?,
        problem: String?,
    ) {
        val canRaise = SnipeRules.maxOrLeadRefusal(snipe, clock.nowUtcMillis()) == null
        val detail = texts.priceOverMaxDetail(snipe, current, minimumNext, problem, canRaise)
        val builder =
            alert(snipe.id, R.drawable.ic_notification_trending_up)
                .setContentTitle(texts.priceOverMaxTitle())
                .setContentText(problem ?: texts.priceOverMaxText(snipe, current))
                .setStyle(Notification.BigTextStyle().bigText(detail))
                .setCategory(Notification.CATEGORY_STATUS)
        if (canRaise) builder.addAction(raiseMaxAction(snipe, current, minimumNext))
        post(NotificationIds.priceAlert(snipe.id), builder.addAction(openAction(snipe.id)).build())
    }

    internal fun showRaiseMaxDone(
        snipe: Snipe,
        newMax: Money,
    ) {
        val notification =
            alert(snipe.id, R.drawable.ic_notification_trending_up)
                .setContentTitle(texts.raiseMaxDoneTitle(newMax))
                .setContentText(texts.raiseMaxDoneText(snipe))
                .setCategory(Notification.CATEGORY_STATUS)
                .setTimeoutAfter(CONFIRMATION_TIMEOUT_MILLIS)
                .build()
        post(NotificationIds.priceAlert(snipe.id), notification)
    }

    override fun needsUser(
        snipe: Snipe,
        reason: NeedsUserReason,
        detail: String?,
    ) {
        val open = openSnipe(snipe.id)
        val builder =
            alert(snipe.id, R.drawable.ic_notification_error)
                .setContentTitle(texts.needsUserTitle(reason))
                .setContentText(texts.needsUserText(reason, detail))
                .setStyle(Notification.BigTextStyle().bigText(texts.needsUserDetail(snipe, reason, detail)))
                .setCategory(Notification.CATEGORY_ERROR)
                .addAction(openAction(snipe.id))
        // Only when the bid is minutes away is it worth taking over a locked screen.
        if (snipe.endTimeUtcMillis - clock.nowUtcMillis() <= IMMINENT_MILLIS && canUseFullScreenIntentSafely()) {
            builder.setFullScreenIntent(open, true)
        }
        post(NotificationIds.needsUser(snipe.id), builder.build())
    }

    internal fun showCancelRefused(
        snipe: Snipe,
        reason: String,
    ) {
        val notification =
            alert(snipe.id, R.drawable.ic_notification_error)
                .setContentTitle(texts.cancelRefusedTitle())
                .setContentText(reason)
                .setStyle(Notification.BigTextStyle().bigText("$reason\n${snipe.title}"))
                .setCategory(Notification.CATEGORY_STATUS)
                .build()
        post(NotificationIds.needsUser(snipe.id), notification)
    }

    /** Android blocked the service from starting in the background; a tap starts it, which Android allows. */
    internal fun showTapToArm(request: SnipeRequest) {
        if (request.action == SnipeAction.CHECK) return
        val start =
            PendingIntent.getForegroundService(
                context,
                NotificationIds.requestCode(request.snipeId, NotificationIds.Slot.TAP_TO_ARM),
                SnipeService.intent(context, request),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            Notification
                .Builder(context, NotificationChannels.ATTENTION)
                .setSmallIcon(R.drawable.ic_notification_schedule)
                .setContentTitle(texts.tapToArmTitle(request.action))
                .setContentText(texts.tapToArmText(request.title, request.endTimeUtcMillis))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(start)
                .build()
        post(NotificationIds.tapToArm(request.snipeId), notification)
    }

    /** Posted before the first unlock after a reboot, from device-protected data only. */
    internal fun showUnlockToArm(
        count: Int,
        nextEndUtcMillis: Long,
    ) {
        val notification =
            Notification
                .Builder(context, NotificationChannels.ATTENTION)
                .setSmallIcon(R.drawable.ic_notification_lock)
                .setContentTitle(texts.unlockTitle())
                .setContentText(texts.unlockText(count, nextEndUtcMillis))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build()
        post(NotificationIds.UNLOCK_TO_ARM, notification)
    }

    internal fun cancelUnlockToArm() = cancel(NotificationIds.UNLOCK_TO_ARM)

    override fun showScheduleProblem(problem: ScheduleProblem) {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        val fix =
            if (problem == ScheduleProblem.EXACT_ALARMS_DENIED) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
            } else {
                context.packageManager.getLaunchIntentForPackage(context.packageName)
            }
        val builder =
            Notification
                .Builder(context, NotificationChannels.ATTENTION)
                .setSmallIcon(R.drawable.ic_notification_error)
                .setContentTitle(texts.scheduleProblemTitle(problem))
                .setContentText(texts.scheduleProblemText(problem))
                .setCategory(Notification.CATEGORY_ERROR)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
        if (fix != null) {
            val flags = PendingIntent.FLAG_IMMUTABLE
            builder.setContentIntent(PendingIntent.getActivity(context, NotificationIds.SCHEDULE_PROBLEM, fix, flags))
        }
        post(NotificationIds.SCHEDULE_PROBLEM, builder.build())
    }

    override fun clearScheduleProblem() = cancel(NotificationIds.SCHEDULE_PROBLEM)

    // Results.

    override fun result(
        snipe: Snipe,
        result: SnipeResult,
    ) {
        // While the window is still up, its chip shows the outcome until the window closes.
        if (hasWindow(snipe.id)) updateWindow(snipe) { it.outcome = result.outcome }
        cancel(NotificationIds.priceAlert(snipe.id))
        cancel(NotificationIds.needsUser(snipe.id))
        cancel(NotificationIds.tapToArm(snipe.id))
        val resultId = NotificationIds.result(snipe.id)
        post(resultId, buildResult(snipe, result, photos.cached(snipe.imageUrl)))
        loadPhoto(snipe) { photo ->
            // Add the photo only while the user still has the result; never bring back one they dismissed.
            if (isShowing(resultId)) post(resultId, buildResult(snipe, result, photo))
        }
    }

    private fun buildResult(
        snipe: Snipe,
        result: SnipeResult,
        photo: Bitmap?,
    ): Notification {
        val text = texts.resultText(snipe, result)
        val won = result.outcome == SnipeOutcome.WON
        val icon = if (won) R.drawable.ic_notification_trophy else R.drawable.ic_notification_flag
        return Notification
            .Builder(context, NotificationChannels.RESULTS)
            .setSmallIcon(icon)
            .setContentTitle(texts.resultTitle(snipe, result))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText("$text\n${snipe.title}"))
            .setCategory(Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openSnipe(snipe.id))
            .apply { photo?.let { setLargeIcon(it) } }
            .build()
    }

    // The quiet service notification and clean-up.

    internal fun serviceNotification(requests: List<SnipeRequest>): Notification =
        Notification
            .Builder(context, NotificationChannels.BACKGROUND)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle(texts.serviceTitle(requests.firstOrNull()?.action))
            .setContentText(texts.serviceText(requests.mapNotNull { it.title }.distinct()))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(false)
            .build()

    /** Removes everything about a snipe the user cancelled: the window, alerts and tap-to-arm. Results stay. */
    internal fun cancelForSnipe(snipeId: Long) {
        windowClosed(snipeId)
        cancel(NotificationIds.priceAlert(snipeId))
        cancel(NotificationIds.needsUser(snipeId))
        cancel(NotificationIds.tapToArm(snipeId))
    }

    internal fun cancelPriceAlert(snipeId: Long) = cancel(NotificationIds.priceAlert(snipeId))

    // Building blocks.

    private fun alert(
        snipeId: Long,
        icon: Int,
    ): Notification.Builder =
        Notification
            .Builder(context, NotificationChannels.ATTENTION)
            .setSmallIcon(icon)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openSnipe(snipeId))

    private fun raiseMaxAction(
        snipe: Snipe,
        current: Money?,
        minimumNext: Money?,
    ): Notification.Action {
        val currency =
            snipe.maxBid?.currency ?: snipe.lastPrice?.currency ?: current?.currency ?: snipe.ref.site.currency
        val input = RemoteInput.Builder(NotificationActions.KEY_NEW_MAX).setLabel(texts.raiseMaxLabel(currency)).build()
        // The system writes the typed text into the intent, so this one PendingIntent must be mutable.
        val reply =
            PendingIntent.getBroadcast(
                context,
                NotificationIds.requestCode(snipe.id, NotificationIds.Slot.RAISE_MAX),
                NotificationActions.raiseMax(context, snipe.id, currency, current, minimumNext),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Action
            .Builder(Icon.createWithResource(context, R.drawable.ic_action_edit), RAISE_MAX_LABEL, reply)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun openAction(snipeId: Long): Notification.Action =
        Notification.Action
            .Builder(Icon.createWithResource(context, R.drawable.ic_action_open), OPEN_LABEL, openSnipe(snipeId))
            .build()

    private fun cancelAction(snipeId: Long): Notification.Action {
        val cancel = broadcast(NotificationActions.cancelSnipe(context, snipeId), snipeId, NotificationIds.Slot.CANCEL)
        return Notification.Action
            .Builder(Icon.createWithResource(context, R.drawable.ic_action_close), CANCEL_LABEL, cancel)
            .build()
    }

    private fun openSnipe(snipeId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            NotificationIds.requestCode(snipeId, NotificationIds.Slot.OPEN),
            AppLinks.openSnipe(context, snipeId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(
        intent: Intent,
        snipeId: Long,
        slot: NotificationIds.Slot,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            NotificationIds.requestCode(snipeId, slot),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun loadPhoto(
        snipe: Snipe,
        use: (Bitmap) -> Unit,
    ) {
        val url = snipe.imageUrl ?: return
        if (photos.cached(url) != null) return
        scope.launch {
            try {
                photos.load(url)?.let(use)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A photo is a nicety; failing to add one must never disturb a running snipe.
                Log.w(TAG, "Could not add the item photo", error)
            }
        }
    }

    /** Converts true UTC to the phone's own clock, which is what notification times are drawn against. */
    private fun toDeviceClock(trueUtcMillis: Long): Long = systemUtcMillis() + (trueUtcMillis - clock.nowUtcMillis())

    private fun canUseFullScreenIntentSafely(): Boolean =
        try {
            canUseFullScreenIntent()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not check the full-screen intent permission", error)
            false
        }

    private fun isShowing(id: Int): Boolean =
        try {
            manager?.activeNotifications?.any { it.id == id } == true
        } catch (error: RuntimeException) {
            false
        }

    private fun post(
        id: Int,
        notification: Notification,
    ) {
        try {
            manager?.notify(id, notification)
        } catch (error: RuntimeException) {
            // A notification that cannot be shown must never take a running snipe down with it.
            Log.e(TAG, "Could not post notification $id", error)
        }
    }

    private fun cancel(id: Int) {
        try {
            manager?.cancel(id)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not cancel notification $id", error)
        }
    }

    private companion object {
        const val TAG = "SnipeNotifications"
        const val RAISE_MAX_LABEL = "Raise max"
        const val OPEN_LABEL = "Open"
        const val CANCEL_LABEL = "Cancel snipe"
        const val IMMINENT_MILLIS = 5 * 60_000L
        const val CONFIRMATION_TIMEOUT_MILLIS = 5 * 60_000L
        const val WINDOW_NOTIFICATION_LINGER_MILLIS = 60 * 60_000L
        const val MINIMUM_TIMEOUT_MILLIS = 5 * 60_000L
    }
}

/** Where the window Live Update's progress bar stands. Four equal segments end at the points below. */
internal enum class WindowProgress(
    val position: Int,
) {
    CHECKED(25),
    READY(50),
    BID(75),
    RESULT(100),
    ;

    companion object {
        val points: List<WindowProgress> = entries
        val segmentLengths: List<Int> = List(entries.size) { SEGMENT_LENGTH }
        private const val SEGMENT_LENGTH = 25

        /** Null while getting ready (the bar is indeterminate until the window really opens). */
        fun progressFor(
            phase: WindowPhase?,
            outcome: SnipeOutcome?,
        ): Int? {
            if (outcome != null) return RESULT.position
            return when (phase) {
                null -> null
                WindowPhase.CHECKING -> 10
                WindowPhase.READY -> READY.position
                WindowPhase.BIDDING -> 60
                WindowPhase.BID_SENT -> BID.position
                WindowPhase.LEADING, WindowPhase.OUTBID, WindowPhase.EXTENDED -> 85
                WindowPhase.RESOLVING -> 95
            }
        }
    }
}
