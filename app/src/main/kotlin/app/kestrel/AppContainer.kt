package app.kestrel

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import androidx.work.WorkManager
import app.kestrel.data.ArmedSnipesCopy
import app.kestrel.data.KestrelDatabase
import app.kestrel.data.SettingsStore
import app.kestrel.data.SnipeRepository
import app.kestrel.ebay.BidIncrements
import app.kestrel.ebay.MoneyText
import app.kestrel.engine.BidPageFactory
import app.kestrel.engine.DryRun
import app.kestrel.engine.EngineClock
import app.kestrel.engine.ListingCheck
import app.kestrel.engine.SnipeEngine
import app.kestrel.engine.SntpClient
import app.kestrel.engine.SntpSample
import app.kestrel.engine.TimeSources
import app.kestrel.engine.TrueTime
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.platform.AndroidAlarmPermissions
import app.kestrel.platform.AndroidReadinessProbes
import app.kestrel.platform.AndroidTimeSources
import app.kestrel.platform.AndroidWakeLocks
import app.kestrel.platform.BackgroundCheck
import app.kestrel.platform.DiskCachedItemPhotos
import app.kestrel.platform.EngineJobRunner
import app.kestrel.platform.JobAftermath
import app.kestrel.platform.JobKind
import app.kestrel.platform.LockedBootNotice
import app.kestrel.platform.MonitorSchedulePolicy
import app.kestrel.platform.NotificationActionHandler
import app.kestrel.platform.NotificationTexts
import app.kestrel.platform.RaiseMaxRules
import app.kestrel.platform.Readiness
import app.kestrel.platform.ReadinessProbes
import app.kestrel.platform.RescheduleWorker
import app.kestrel.platform.SchedulePlanner
import app.kestrel.platform.SchedulePolicy
import app.kestrel.platform.SnipeActions
import app.kestrel.platform.SnipeJobAftermath
import app.kestrel.platform.SnipeJobRegistry
import app.kestrel.platform.SnipeJobRunner
import app.kestrel.platform.SnipeNotifications
import app.kestrel.platform.SnipeScheduler
import app.kestrel.platform.SystemEventHandler
import app.kestrel.platform.WakeLocks
import app.kestrel.web.EbaySession
import app.kestrel.web.WebBidPageFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. Each layer adds its singletons here. Everything is lazy: the process can start before
 * the first unlock after a reboot (direct boot), when the database and the WebView cookies cannot be opened yet.
 */
open class AppContainer(
    val context: Context,
) {
    /** For work that must outlive a receiver or a screen, such as rescheduling after a reboot. */
    val applicationScope: CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, error -> Log.e("AppContainer", "Background work failed", error) },
        )

    // Data.

    val database: KestrelDatabase by lazy { KestrelDatabase.open(context) }
    val armedSnipesCopy: ArmedSnipesCopy by lazy { ArmedSnipesCopy.inDeviceProtectedStorage(context) }
    val settings: SettingsStore by lazy { SettingsStore.create(context) }
    val repository: SnipeRepository by lazy {
        SnipeRepository(database, armedSnipesCopy, nowUtcMillis = { clock.nowUtcMillis() }, formatMoney = formatMoney)
    }

    // Time.

    open val timeSources: TimeSources by lazy { AndroidTimeSources(sntpExchange = sntpExchange) }
    val trueTime: TrueTime by lazy { TrueTime(timeSources) }

    /** True time for everything the platform schedules and records. */
    open val clock: EngineClock get() = trueTime

    // Engine and pages.

    val bidPages: BidPageFactory by lazy { WebBidPageFactory(context) }
    val snipeEngine: SnipeEngine by lazy { SnipeEngine(repository, notifications, trueTime, bidPages) }
    val dryRun: DryRun by lazy { DryRun(repository, notifications, clock) }
    val listingCheck: ListingCheck by lazy { ListingCheck(repository, notifications, clock) }

    // Platform.

    val notifications: SnipeNotifications by lazy {
        SnipeNotifications(
            context = context,
            clock = clock,
            systemUtcMillis = System::currentTimeMillis,
            texts = notificationTexts,
            photos = DiskCachedItemPhotos(context),
            scope = applicationScope,
            canUseFullScreenIntent = ::canUseFullScreenIntent,
        )
    }

    val scheduler: SnipeScheduler by lazy {
        SnipeScheduler(
            context = context,
            clock = clock,
            systemUtcMillis = System::currentTimeMillis,
            planner = SchedulePlanner(schedulePolicy),
            pendingSnipes = { repository.pendingSnipes() },
            permissions = AndroidAlarmPermissions(context),
            workManager = { WorkManager.getInstance(context) },
            problemNotifier = notifications,
            isWindowRunning = ::isWindowRunning,
        )
    }

    val readiness: Readiness by lazy { Readiness(context, readinessProbes, notificationTexts::dayAndTime) }

    /** The UI changes snipes through this, so alarms, running jobs and notifications always follow. */
    val snipeActions: SnipeActions by lazy {
        SnipeActions(
            repository = repository,
            reschedule = { scheduler.schedule(it) },
            cancelSchedule = { scheduler.cancel(it) },
            stopJobs = { jobRegistry.cancelAll(it) },
            clearNotifications = { notifications.cancelForSnipe(it) },
        )
    }

    internal open val notificationTexts: NotificationTexts by lazy {
        NotificationTexts(formatMoney = formatMoney, use24HourClock = { DateFormat.is24HourFormat(context) })
    }

    internal open val schedulePolicy: SchedulePolicy = MonitorSchedulePolicy

    internal open val jobRunner: SnipeJobRunner by lazy {
        EngineJobRunner(
            store = repository,
            engine = { snipeEngine },
            dryRun = { dryRun },
            listingCheck = { listingCheck },
            pages = { bidPages },
        )
    }

    internal val jobRegistry: SnipeJobRegistry = SnipeJobRegistry()

    internal fun isWindowRunning(snipeId: Long): Boolean = JobKind.WINDOW in jobRegistry.runningKinds(snipeId)

    internal open val wakeLocks: WakeLocks by lazy { AndroidWakeLocks(context) }

    internal val jobAftermath: JobAftermath by lazy {
        SnipeJobAftermath(
            store = repository,
            notifier = notifications,
            reschedule = { snipe, lastCheckAttempt -> scheduler.schedule(snipe, lastCheckAttempt) },
            cancelSchedule = { scheduler.cancel(it) },
            nowUtcMillis = { clock.nowUtcMillis() },
        )
    }

    internal val backgroundCheck: BackgroundCheck by lazy {
        BackgroundCheck(
            runner = jobRunner,
            store = repository,
            reschedule = { snipe, lastCheckAttempt -> scheduler.schedule(snipe, lastCheckAttempt) },
            jobs = jobRegistry,
            nowUtcMillis = { clock.nowUtcMillis() },
        )
    }

    internal val systemEvents: SystemEventHandler by lazy {
        SystemEventHandler(
            rescheduleAll = { scheduler.rescheduleAll() },
            purgeDeletedBefore = { repository.purgeDeleted(it) },
            cancelUnlockNotice = { notifications.cancelUnlockToArm() },
            finishLater = { RescheduleWorker.enqueue(context) },
            nowUtcMillis = { clock.nowUtcMillis() },
        )
    }

    internal val lockedBootNotice: LockedBootNotice by lazy {
        LockedBootNotice(armedSnipesCopy, notifications, systemUtcMillis = System::currentTimeMillis)
    }

    internal val notificationActions: NotificationActionHandler by lazy {
        NotificationActionHandler(
            repository = repository,
            actions = snipeActions,
            notifications = notifications,
            rules = RaiseMaxRules(parseAmount = parseMoney, formatMoney = formatMoney),
            minimumNextBid = minimumNextBid,
        )
    }

    internal open val readinessProbes: ReadinessProbes by lazy {
        AndroidReadinessProbes(
            context = context,
            repository = repository,
            settings = settings,
            sessionCookies = { host -> EbaySession.hasCookies(host) },
            clockSync = { trueTime.lastSync },
            elapsedRealtime = SystemClock::elapsedRealtime,
        )
    }

    // eBay display and input rules the platform uses.

    internal open val formatMoney: (Money) -> String = { MoneyText.format(it) }

    internal open val parseMoney: (String, String) -> Money? = { text, currency ->
        MoneyText.parseUserInput(
            text,
            currency,
        )
    }

    internal open val minimumNextBid: (Snipe) -> Money? = { snipe ->
        // A Buyer Protection fee on the snipe means eBay's displayed prices include it.
        val feeIncluded = snipe.buyerProtectionFee != null
        snipe.lastPrice?.let { BidIncrements.minimumNextBid(snipe.ref.site, it, snipe.lastBidCount ?: 0, feeIncluded) }
    }

    private fun canUseFullScreenIntent(): Boolean =
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    private val sntpClient by lazy { SntpClient(System::currentTimeMillis, SystemClock::elapsedRealtime) }

    // One SNTP exchange with the host, or null on failure (TrueTime then falls back to network time, then the phone clock).
    private val sntpExchange: suspend (String) -> SntpSample? = { host -> sntpClient.query(host) }
}
