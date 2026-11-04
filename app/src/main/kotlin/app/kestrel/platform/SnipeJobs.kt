package app.kestrel.platform

import android.content.Context
import android.os.PowerManager
import android.util.Log
import app.kestrel.engine.BidPage
import app.kestrel.engine.BidPageFactory
import app.kestrel.engine.DryRun
import app.kestrel.engine.ListingCheck
import app.kestrel.engine.SnipeEngine
import app.kestrel.engine.SnipeStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The three kinds of work the service runs. A window outranks a dry run, which outranks a check. */
internal enum class JobKind(
    val rank: Int,
    val timeoutMillis: Long,
    val label: String,
) {
    CHECK(rank = 0, timeoutMillis = 3 * MINUTE_MILLIS, label = "Check"),
    DRY_RUN(rank = 1, timeoutMillis = 5 * MINUTE_MILLIS, label = "Dry run"),

    // Extended bidding can push the end out, so the window gets a generous ceiling.
    WINDOW(rank = 2, timeoutMillis = 40 * MINUTE_MILLIS, label = "Bid window"),
    ;

    /** The wake lock outlives the job's own timeout slightly, so a timed-out job can still clean up awake. */
    val wakeLockMillis: Long get() = timeoutMillis + MINUTE_MILLIS
}

private const val MINUTE_MILLIS = 60_000L

internal val SnipeAction.jobKind: JobKind
    get() =
        when (this) {
            SnipeAction.CHECK -> JobKind.CHECK
            SnipeAction.DRY_RUN -> JobKind.DRY_RUN
            SnipeAction.WINDOW, SnipeAction.WINDOW_BACKUP -> JobKind.WINDOW
        }

/** Runs one job against the engine. Called on the main thread, which the WebViews need. */
internal fun interface SnipeJobRunner {
    suspend fun run(
        kind: JobKind,
        snipeId: Long,
    )
}

/**
 * The production runner: the engine's window, dry run and check, each with its own page. A snipe that was deleted
 * or already finished (a stale alarm) gets no page load at all.
 */
internal class EngineJobRunner(
    private val store: SnipeStore,
    private val engine: () -> SnipeEngine,
    private val dryRun: () -> DryRun,
    private val listingCheck: () -> ListingCheck,
    private val pages: () -> BidPageFactory,
) : SnipeJobRunner {
    override suspend fun run(
        kind: JobKind,
        snipeId: Long,
    ) {
        val snipe = store.snipe(snipeId)
        if (snipe == null || snipe.status.isFinal) {
            Log.i(TAG, "Skipped ${kind.label} for snipe $snipeId: it is ${snipe?.status ?: "gone"}")
            return
        }
        when (kind) {
            // The engine opens and closes the window's own page.
            JobKind.WINDOW -> engine().runWindow(snipeId)

            // A dry run only ever sees the preview type, so it has no way to submit.
            JobKind.DRY_RUN -> withPage(pages()) { page -> dryRun().run(snipeId, page) }

            JobKind.CHECK -> withPage(pages()) { page -> listingCheck().check(snipeId, page) }
        }
    }

    private companion object {
        const val TAG = "EngineJobRunner"
    }
}

/** Creates a page on the main thread, uses it, and always closes it, even when the job fails or is cancelled. */
internal suspend fun withPage(
    factory: BidPageFactory,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    use: suspend (BidPage) -> Unit,
) {
    val page = withContext(mainDispatcher) { factory.create() }
    try {
        use(page)
    } finally {
        withContext(NonCancellable + mainDispatcher) { page.close() }
    }
}

internal data class JobKey(
    val snipeId: Long,
    val kind: JobKind,
)

/**
 * The jobs the service is running, shared across the process so a notification action can stop a snipe's job and a
 * background check can see that the service already has the page open.
 */
internal class SnipeJobRegistry {
    private class Entry(
        val request: SnipeRequest,
        val job: Job,
    )

    private val entries = LinkedHashMap<JobKey, Entry>()

    @Synchronized
    fun register(
        key: JobKey,
        request: SnipeRequest,
        job: Job,
    ): Boolean {
        if (entries.containsKey(key)) return false
        entries[key] = Entry(request, job)
        return true
    }

    @Synchronized
    fun unregister(
        key: JobKey,
        job: Job,
    ) {
        if (entries[key]?.job === job) entries.remove(key)
    }

    @Synchronized
    fun runningKinds(snipeId: Long): Set<JobKind> =
        entries.keys
            .filter { it.snipeId == snipeId }
            .map { it.kind }
            .toSet()

    @Synchronized
    fun isRunning(snipeId: Long): Boolean = entries.keys.any { it.snipeId == snipeId }

    /** Requests of running jobs, oldest first. */
    @Synchronized
    fun runningRequests(): List<SnipeRequest> = entries.values.map { it.request }

    /** Windows in the order they started; the first one owns the foreground notification. */
    @Synchronized
    fun runningWindows(): List<SnipeRequest> =
        entries.filterKeys { it.kind == JobKind.WINDOW }.values.map { it.request }

    @Synchronized
    fun isEmpty(): Boolean = entries.isEmpty()

    fun cancel(key: JobKey) {
        val job = synchronized(this) { entries[key]?.job }
        job?.cancel()
    }

    /** Stops every job of the snipe, for example when the user cancels it. */
    fun cancelAll(snipeId: Long) {
        val jobs = synchronized(this) { entries.filterKeys { it.snipeId == snipeId }.values.map { it.job } }
        jobs.forEach { it.cancel() }
    }
}

/** A partial wake lock with a timeout, held for one job. */
internal fun interface WakeLocks {
    fun acquire(
        tag: String,
        timeoutMillis: Long,
    ): HeldWakeLock
}

internal fun interface HeldWakeLock {
    fun release()
}

internal class AndroidWakeLocks(
    context: Context,
) : WakeLocks {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    override fun acquire(
        tag: String,
        timeoutMillis: Long,
    ): HeldWakeLock {
        val lock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag) ?: return HeldWakeLock {}
        lock.setReferenceCounted(false)
        lock.acquire(timeoutMillis)
        return HeldWakeLock { if (lock.isHeld) lock.release() }
    }
}
