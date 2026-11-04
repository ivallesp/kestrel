package app.kestrel.engine

import app.kestrel.model.Snipe
import app.kestrel.model.SnipeResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

// Public API of the engine. The procedures behind it live in the other files of this package.

/** Runs one snipe's final window (T-120 s to the resolved result). The caller holds a wake lock and a foreground service. */
class SnipeEngine internal constructor(
    private val store: SnipeStore,
    private val notifier: SnipeNotifier,
    private val clock: TrueTime,
    private val pages: BidPageFactory,
    private val minimumBidRule: MinimumBidRule,
) {
    constructor(
        store: SnipeStore,
        notifier: SnipeNotifier,
        clock: TrueTime,
        pages: BidPageFactory,
    ) : this(store, notifier, clock, pages, MinimumBidRule.Ebay)

    /**
     * Prepares, places and follows the bid, and returns the result it also stores and notifies. A second call for a
     * snipe whose window is still running waits for that window instead of starting another one. When the stored snipe
     * is not final afterwards (the end moved far away, or the result is still unknown), schedule it again.
     */
    suspend fun runWindow(snipeId: Long): SnipeResult =
        WindowGuard.runExclusive(snipeId) {
            SnipeWindow(snipeId, store, notifier, clock, pages, minimumBidRule).run()
        }
}

/** About ten minutes before the end: opens the bid layer and types the max, never submits. */
class DryRun internal constructor(
    private val store: SnipeStore,
    private val notifier: SnipeNotifier,
    private val clock: EngineClock,
    private val minimumBidRule: MinimumBidRule,
) {
    constructor(
        store: SnipeStore,
        notifier: SnipeNotifier,
        clock: EngineClock,
    ) : this(store, notifier, clock, MinimumBidRule.Ebay)

    /** The dry run only ever sees a [BidLayerPreview], so it has no way to submit. The caller owns and closes the page. */
    suspend fun run(
        snipeId: Long,
        preview: BidLayerPreview,
    ): DryRunOutcome = DryRunProcedure(store, notifier, clock, minimumBidRule).run(snipeId, preview)
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
class ListingCheck internal constructor(
    private val store: SnipeStore,
    private val notifier: SnipeNotifier,
    private val clock: EngineClock,
    private val minimumBidRule: MinimumBidRule,
) {
    constructor(
        store: SnipeStore,
        notifier: SnipeNotifier,
        clock: EngineClock,
    ) : this(store, notifier, clock, MinimumBidRule.Ebay)

    /**
     * Every attempt that tried to load eBay, failed or not, sets lastCheckedAtUtcMillis, so scheduling the next check
     * with [MonitorPolicy.nextCheckAt] backs off on its own. The caller owns and closes the page.
     */
    suspend fun check(
        snipeId: Long,
        preview: BidLayerPreview,
    ): CheckOutcome = ListingCheckProcedure(store, notifier, clock, minimumBidRule).check(snipeId, preview)
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

/**
 * When to look at a listing again, when to dry-run and when to open the window. Times are UTC epoch ms.
 *
 * Cadence by time left: more than 24 h every 6 h; 6-24 h every 2 h; 1-6 h hourly; 10-60 min every 15 min; then the
 * dry run at T-10 min and the window from T-120 s. Each interval carries a +-10 % jitter derived from the end time and
 * the last check, so recomputing gives the same answer. A check never jumps over the start of a faster band.
 */
object MonitorPolicy {
    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val JITTER_FRACTION = 0.10

    private class Band(
        val moreThanMillis: Long,
        val intervalMillis: Long,
    )

    private val bands =
        listOf(
            Band(moreThanMillis = 24 * HOUR_MILLIS, intervalMillis = 6 * HOUR_MILLIS),
            Band(moreThanMillis = 6 * HOUR_MILLIS, intervalMillis = 2 * HOUR_MILLIS),
            Band(moreThanMillis = HOUR_MILLIS, intervalMillis = HOUR_MILLIS),
            Band(moreThanMillis = 10 * MINUTE_MILLIS, intervalMillis = 15 * MINUTE_MILLIS),
        )

    /**
     * The next background check, [nowUtcMillis] when one is due (never checked, or overdue), or null when the next
     * check would fall after the window opens (the dry run and the window take over from there).
     */
    fun nextCheckAt(
        nowUtcMillis: Long,
        endUtcMillis: Long,
        lastCheckUtcMillis: Long?,
    ): Long? {
        val windowOpens = windowOpensAt(endUtcMillis)
        if (nowUtcMillis >= windowOpens) return null
        if (lastCheckUtcMillis == null) return nowUtcMillis
        val bandIndex = bands.indexOfFirst { endUtcMillis - lastCheckUtcMillis > it.moreThanMillis }
        if (bandIndex < 0) return null
        val band = bands[bandIndex]
        val jitter = 1.0 + JITTER_FRACTION * (2 * stableFraction(endUtcMillis, lastCheckUtcMillis) - 1)
        var next = lastCheckUtcMillis + (band.intervalMillis * jitter).toLong()
        val fasterBand = bands.getOrNull(bandIndex + 1)
        val fasterBandStarts = endUtcMillis - band.moreThanMillis
        if (fasterBand != null && next > fasterBandStarts) {
            // Do not skip the faster band, but do not check twice within its own interval either.
            next = maxOf(fasterBandStarts, lastCheckUtcMillis + (fasterBand.intervalMillis * jitter).toLong())
        }
        next = maxOf(next, nowUtcMillis)
        return if (next >= windowOpens) null else next
    }

    fun dryRunAt(endUtcMillis: Long): Long = endUtcMillis - 10 * MINUTE_MILLIS

    fun windowOpensAt(endUtcMillis: Long): Long = endUtcMillis - 120_000L

    fun backupWindowAt(endUtcMillis: Long): Long = endUtcMillis - 75_000L
}

/**
 * SNTP-backed true time. True UTC is anchored to elapsedRealtime, so changes to the phone's wall clock never move it.
 * Before the first [sync] it uses a provisional anchor from Android's network time, else the system clock.
 */
class TrueTime(
    private val sources: TimeSources,
) : EngineClock {
    private val syncLock = Mutex()
    private val anchor = AtomicReference<TimeAnchor?>(null)

    @Volatile
    private var latestSync: ClockSync? = null

    override fun nowUtcMillis(): Long = utcAtElapsedRealtime(sources.elapsedRealtimeMillis())

    override fun elapsedRealtimeMillis(): Long = sources.elapsedRealtimeMillis()

    /** True UTC at another elapsedRealtime instant, for example when a tap was dispatched. */
    fun utcAtElapsedRealtime(elapsedRealtimeMillis: Long): Long = currentAnchor().utcAt(elapsedRealtimeMillis)

    /** The elapsedRealtime instant of a true UTC time, for ELAPSED_REALTIME alarms. */
    fun elapsedRealtimeAtUtc(utcMillis: Long): Long = currentAnchor().elapsedRealtimeAt(utcMillis)

    /**
     * Measures the clock offset now (several SNTP samples against time.google.com). Falls back to a recent SNTP
     * measurement, then Android's network time, then the system clock, and says which in [ClockSync.source].
     * Returns null only when no source gives a plausible time. Concurrent calls share one measurement, and an SNTP
     * measurement younger than a minute is reused.
     */
    suspend fun sync(): ClockSync? = syncLock.withLock { measure() }

    val lastSync: ClockSync? get() = latestSync

    private suspend fun measure(): ClockSync? {
        val previous = latestSync
        val previousAnchor = anchor.get()
        val elapsedNow = sources.elapsedRealtimeMillis()
        if (previous != null &&
            previous.source == ClockSources.SNTP &&
            elapsedNow - previous.atElapsedRealtimeMillis < ClockSampling.REUSE_SNTP_SYNC_FOR_MILLIS
        ) {
            return previous
        }
        val best = ClockSampling.bestSample(sources) { delay(it) }
        val elapsed = sources.elapsedRealtimeMillis()
        val systemNow = sources.systemUtcMillis()
        val (newAnchor, sync) =
            when {
                best != null -> {
                    best.anchor to
                        ClockSync(
                            offsetMillis = best.sample.offsetMillis,
                            roundTripMillis = best.sample.roundTripMillis,
                            source = ClockSources.SNTP,
                            atElapsedRealtimeMillis = best.anchor.elapsedRealtimeMillis,
                        )
                }

                previous != null &&
                    previousAnchor != null &&
                    !previousAnchor.provisional &&
                    previousAnchor.source == ClockSources.SNTP &&
                    elapsed - previousAnchor.elapsedRealtimeMillis < ClockSampling.KEEP_EARLIER_SNTP_FOR_MILLIS -> {
                    previousAnchor to
                        ClockSync(
                            offsetMillis = previousAnchor.utcAt(elapsed) - systemNow,
                            roundTripMillis = previous.roundTripMillis,
                            source = ClockSources.SNTP_EARLIER,
                            atElapsedRealtimeMillis = previousAnchor.elapsedRealtimeMillis,
                        )
                }

                else -> {
                    fallbackAnchor(provisional = false) to null
                }
            }
        // An implausible time is never installed: other windows share this clock and must keep a sane one.
        if (newAnchor.utcAt(elapsed) < ClockSampling.EARLIEST_PLAUSIBLE_UTC_MILLIS) return null
        anchor.set(newAnchor)
        val result =
            sync
                ?: ClockSync(
                    offsetMillis = newAnchor.utcAt(elapsed) - systemNow,
                    roundTripMillis = 0,
                    source = newAnchor.source,
                    atElapsedRealtimeMillis = newAnchor.elapsedRealtimeMillis,
                )
        latestSync = result
        return result
    }

    private fun currentAnchor(): TimeAnchor {
        while (true) {
            val current = anchor.get()
            if (current != null && !current.isStale(sources.elapsedRealtimeMillis())) return current
            val fresh = fallbackAnchor(provisional = true)
            // Only replace what was read, so a sync finishing at the same moment is never overwritten.
            if (anchor.compareAndSet(current, fresh)) return fresh
        }
    }

    private fun fallbackAnchor(provisional: Boolean): TimeAnchor {
        val elapsed = sources.elapsedRealtimeMillis()
        val network =
            try {
                sources.networkUtcMillis()
            } catch (error: Exception) {
                null
            }
        if (network != null && network >= ClockSampling.EARLIEST_PLAUSIBLE_UTC_MILLIS) {
            return TimeAnchor(network, elapsed, ClockSources.NETWORK, provisional)
        }
        return TimeAnchor(sources.systemUtcMillis(), elapsed, ClockSources.SYSTEM, provisional)
    }
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
    /** The SNTP round trip; 0 when the source is not SNTP. */
    val roundTripMillis: Long,
    /** One of [ClockSources]. */
    val source: String,
    val atElapsedRealtimeMillis: Long,
)
