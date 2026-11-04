package app.kestrel.engine

import kotlinx.coroutines.CancellationException
import kotlin.math.abs

/**
 * [TimeSources] over plain functions, so the platform layer only supplies the Android calls, for example
 * `DeviceTimeSources(System::currentTimeMillis, SystemClock::elapsedRealtime) { SystemClock.currentNetworkTimeClock().millis() }`.
 * SNTP runs in pure Kotlin through [SntpClient].
 */
class DeviceTimeSources(
    private val readSystemUtcMillis: () -> Long,
    private val readElapsedRealtimeMillis: () -> Long,
    private val readNetworkUtcMillis: () -> Long?,
    private val sntpClient: SntpClient = SntpClient(readSystemUtcMillis, readElapsedRealtimeMillis),
) : TimeSources {
    override fun systemUtcMillis(): Long = readSystemUtcMillis()

    override fun elapsedRealtimeMillis(): Long = readElapsedRealtimeMillis()

    // SystemClock.currentNetworkTimeClock() throws DateTimeException when Android has no network time yet.
    override fun networkUtcMillis(): Long? =
        try {
            readNetworkUtcMillis()
        } catch (error: Exception) {
            null
        }

    override suspend fun sntp(host: String): SntpSample? =
        try {
            sntpClient.query(host)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
}

/** Values of [ClockSync.source]. */
object ClockSources {
    /** Google Public NTP serves smeared leap seconds; mixing it with other servers would mix two time scales. */
    const val SNTP_HOST = "time.google.com"

    /** A fresh SNTP measurement against [SNTP_HOST]. */
    const val SNTP = "sntp"

    /** SNTP failed just now, but an SNTP measurement from the last half hour is still the best estimate. */
    const val SNTP_EARLIER = "sntp-earlier"

    /** Android's network time (SystemClock.currentNetworkTimeClock), synced about once a day. */
    const val NETWORK = "network"

    /** The phone's own clock, which Android only corrects when it is more than about 2 s off. */
    const val SYSTEM = "system"
}

/** True UTC at one elapsedRealtime instant. Later readings move with elapsedRealtime only, never with the wall clock. */
internal data class TimeAnchor(
    val utcMillis: Long,
    val elapsedRealtimeMillis: Long,
    val source: String,
    /** Built without a sync, from the network or system clock; rebuilt when it gets old. */
    val provisional: Boolean,
) {
    fun utcAt(elapsedRealtimeMillis: Long): Long = utcMillis + (elapsedRealtimeMillis - this.elapsedRealtimeMillis)

    fun elapsedRealtimeAt(utcMillis: Long): Long = elapsedRealtimeMillis + (utcMillis - this.utcMillis)

    /** A provisional anchor drifts with the phone's oscillator and misses Android's own clock corrections. */
    fun isStale(nowElapsedRealtimeMillis: Long): Boolean =
        provisional &&
            nowElapsedRealtimeMillis - elapsedRealtimeMillis >= ClockSampling.PROVISIONAL_ANCHOR_MAXIMUM_AGE_MILLIS
}

/** Takes SNTP samples for [TrueTime.sync]. */
internal object ClockSampling {
    const val SAMPLE_SPACING_MILLIS = 250L
    const val MINIMUM_SAMPLES = 4
    const val MAXIMUM_ATTEMPTS = 8
    const val MAXIMUM_ROUND_TRIP_MILLIS = 1_000L
    const val SYNC_BUDGET_MILLIS = 8_000L
    const val REUSE_SNTP_SYNC_FOR_MILLIS = 60_000L
    const val KEEP_EARLIER_SNTP_FOR_MILLIS = 30 * 60_000L
    const val PROVISIONAL_ANCHOR_MAXIMUM_AGE_MILLIS = 10 * 60_000L

    /** Reading the two clocks one after the other costs well under this; more means the wall clock was set. */
    const val WALL_CLOCK_STEP_TOLERANCE_MILLIS = 50L

    /** 2026-01-01T00:00:00Z: a clock that reads earlier than this is certainly wrong. */
    const val EARLIEST_PLAUSIBLE_UTC_MILLIS = 1_767_225_600_000L

    /** A sample with the elapsedRealtime instant it describes. */
    data class AnchoredSample(
        val sample: SntpSample,
        val anchor: TimeAnchor,
    )

    /**
     * Four to eight samples a few hundred milliseconds apart; the one with the lowest round trip wins, because its
     * offset has the smallest error bound. Round trips over a second and failed exchanges are dropped.
     */
    suspend fun bestSample(
        sources: TimeSources,
        delayMillis: suspend (Long) -> Unit,
    ): AnchoredSample? {
        val accepted = mutableListOf<AnchoredSample>()
        val deadline = sources.elapsedRealtimeMillis() + SYNC_BUDGET_MILLIS
        var attempts = 0
        while (attempts < MAXIMUM_ATTEMPTS && accepted.size < MINIMUM_SAMPLES) {
            if (attempts > 0) delayMillis(SAMPLE_SPACING_MILLIS)
            val remaining = deadline - sources.elapsedRealtimeMillis()
            if (remaining <= 0) break
            attempts++
            val systemBefore = sources.systemUtcMillis()
            val elapsedBefore = sources.elapsedRealtimeMillis()
            val sample = callWithin(remaining) { sources.sntp(ClockSources.SNTP_HOST) }.valueOrNull() ?: continue
            val elapsed = sources.elapsedRealtimeMillis()
            val system = sources.systemUtcMillis()
            if (sample.roundTripMillis !in 0..MAXIMUM_ROUND_TRIP_MILLIS) continue
            // The offset is relative to the wall clock during the exchange. If Android set the clock meanwhile, adding
            // the offset to the clock as it reads now would count the correction twice, so such a sample is dropped.
            val wallClockStep = (system - systemBefore) - (elapsed - elapsedBefore)
            if (abs(wallClockStep) > WALL_CLOCK_STEP_TOLERANCE_MILLIS) continue
            val trueUtc = system + sample.offsetMillis
            if (trueUtc < EARLIEST_PLAUSIBLE_UTC_MILLIS) continue
            accepted += AnchoredSample(sample, TimeAnchor(trueUtc, elapsed, ClockSources.SNTP, provisional = false))
        }
        return accepted.minByOrNull { it.sample.roundTripMillis }
    }
}
