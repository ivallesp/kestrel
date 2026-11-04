package app.kestrel.engine

import app.kestrel.model.Snipe
import app.kestrel.model.SnipeResult

// Public API of the engine. Owned by the engine lane; bodies are stubs until it lands.

/** Runs one snipe's final window (T-120 s to the resolved result). The caller holds a wake lock and a foreground service. */
class SnipeEngine(
    private val store: SnipeStore,
    private val notifier: SnipeNotifier,
    private val clock: TrueTime,
    private val pages: BidPageFactory,
) {
    suspend fun runWindow(snipeId: Long): SnipeResult = TODO("engine lane")
}

/** About ten minutes before the end: opens the bid layer and types the max, never submits. */
class DryRun(
    private val store: SnipeStore,
    private val notifier: SnipeNotifier,
    private val clock: EngineClock,
) {
    suspend fun run(
        snipeId: Long,
        preview: BidLayerPreview,
    ): DryRunOutcome = TODO("engine lane")
}

sealed interface DryRunOutcome {
    data object Passed : DryRunOutcome

    data class Failed(
        val reason: NeedsUserReason,
        val detail: String,
    ) : DryRunOutcome

    data class Skipped(
        val why: String,
    ) : DryRunOutcome
}

/** A background check of one listing: reload, update price and end time, raise alerts. */
class ListingCheck(
    private val store: SnipeStore,
    private val notifier: SnipeNotifier,
    private val clock: EngineClock,
) {
    suspend fun check(
        snipeId: Long,
        preview: BidLayerPreview,
    ): CheckOutcome = TODO("engine lane")
}

sealed interface CheckOutcome {
    data class Updated(
        val snipe: Snipe,
    ) : CheckOutcome

    data class Ended(
        val snipe: Snipe,
    ) : CheckOutcome

    data class NeedsUser(
        val reason: NeedsUserReason,
    ) : CheckOutcome

    data class Failed(
        val reason: String,
    ) : CheckOutcome
}

/** When to look at a listing again, when to dry-run and when to open the window. Times are UTC epoch ms. */
object MonitorPolicy {
    fun nextCheckAt(
        nowUtcMillis: Long,
        endUtcMillis: Long,
        lastCheckUtcMillis: Long?,
    ): Long? = TODO("engine lane")

    fun dryRunAt(endUtcMillis: Long): Long = TODO("engine lane")

    fun windowOpensAt(endUtcMillis: Long): Long = TODO("engine lane")

    fun backupWindowAt(endUtcMillis: Long): Long = TODO("engine lane")
}

/** SNTP-backed true time. */
class TrueTime(
    private val sources: TimeSources,
) : EngineClock {
    override fun nowUtcMillis(): Long = TODO("engine lane")

    override fun elapsedRealtimeMillis(): Long = TODO("engine lane")

    /** Measures the clock offset now (several SNTP samples). */
    suspend fun sync(): ClockSync? = TODO("engine lane")

    val lastSync: ClockSync? get() = TODO("engine lane")
}

/** Platform time sources, injected so the engine stays testable on the JVM. */
interface TimeSources {
    fun systemUtcMillis(): Long

    fun elapsedRealtimeMillis(): Long

    /** SystemClock.currentNetworkTimeClock() in UTC ms, or null when Android has no network time. */
    fun networkUtcMillis(): Long?

    /** One SNTP exchange with [host]; returns the measured offset and round trip, or null on failure. */
    suspend fun sntp(host: String): SntpSample?
}

data class SntpSample(
    val offsetMillis: Long,
    val roundTripMillis: Long,
)

data class ClockSync(
    /** True UTC minus system UTC. */
    val offsetMillis: Long,
    val roundTripMillis: Long,
    val source: String,
    val atElapsedRealtimeMillis: Long,
)
