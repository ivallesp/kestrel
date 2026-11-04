package app.kestrel.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TrueTimeTest {
    @Test
    fun picksTheLowestRoundTripSampleAndRejectsBadOnes() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            time.sntpScript =
                ArrayDeque(
                    listOf(
                        SntpSample(offsetMillis = -2_500, roundTripMillis = 1_500),
                        SntpSample(offsetMillis = -2_900, roundTripMillis = 80),
                        null,
                        SntpSample(offsetMillis = -1_000, roundTripMillis = -5),
                        SntpSample(offsetMillis = -3_020, roundTripMillis = 40),
                        SntpSample(offsetMillis = -2_980, roundTripMillis = 60),
                    ),
                )
            val clock = TrueTime(time)

            val sync = clock.sync()!!

            assertEquals(-3_020L, sync.offsetMillis)
            assertEquals(40L, sync.roundTripMillis)
            assertEquals(ClockSources.SNTP, sync.source)
            // Three good samples after six tries, so it keeps trying up to eight.
            assertEquals(8, time.sntpCalls)
            assertEquals(time.systemUtcMillis() - 3_020, clock.nowUtcMillis())
            assertEquals(sync, clock.lastSync)
        }

    @Test
    fun stopsAfterFourGoodSamples() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            val clock = TrueTime(time)

            clock.sync()

            assertEquals(4, time.sntpCalls)
            assertEquals(time.trueUtc(), clock.nowUtcMillis())
        }

    @Test
    fun wallClockJumpsDoNotMoveTrueTime() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            val clock = TrueTime(time)
            clock.sync()
            val before = clock.nowUtcMillis()

            time.systemSkewMillis = 3_600_000
            assertEquals(before, clock.nowUtcMillis())
            delay(10_000)
            assertEquals(before + 10_000, clock.nowUtcMillis())
        }

    @Test
    fun fallsBackToNetworkTimeThenSystemTime() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            time.sntpScript = ArrayDeque()
            val clock = TrueTime(time)

            val network = clock.sync()!!
            assertEquals(ClockSources.NETWORK, network.source)
            assertEquals(400L - 3_000L, network.offsetMillis)
            assertEquals(time.trueUtc() + 400, clock.nowUtcMillis())

            time.networkSkewMillis = null
            val system = clock.sync()!!
            assertEquals(ClockSources.SYSTEM, system.source)
            assertEquals(0L, system.offsetMillis)
            assertEquals(time.systemUtcMillis(), clock.nowUtcMillis())
        }

    @Test
    fun recentSntpMeasurementBeatsTheFallbacks() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            val clock = TrueTime(time)
            val first = clock.sync()!!

            // Within a minute the same measurement is reused without asking time.google.com again.
            assertSame(first, clock.sync())
            assertEquals(4, time.sntpCalls)

            delay(5 * 60_000)
            time.sntpScript = ArrayDeque()
            val later = clock.sync()!!
            assertEquals(ClockSources.SNTP_EARLIER, later.source)
            assertEquals(time.trueUtc(), clock.nowUtcMillis())
        }

    @Test
    fun noPlausibleTimeGivesNull() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            time.sntpScript = ArrayDeque()
            time.networkSkewMillis = null
            time.systemSkewMillis = -BASE_UTC
            val clock = TrueTime(time)

            assertNull(clock.sync())
        }

    @Test
    fun beforeTheFirstSyncTimeComesFromNetworkTime() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            val clock = TrueTime(time)

            assertEquals(time.trueUtc() + 400, clock.nowUtcMillis())
            assertNull(clock.lastSync)
        }

    @Test
    fun elapsedRealtimeConversionsUseTheAnchor() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            val clock = TrueTime(time)
            clock.sync()

            assertEquals(time.trueUtc() - 1_000, clock.utcAtElapsedRealtime(time.elapsedRealtimeMillis() - 1_000))
            assertEquals(time.elapsedRealtimeMillis() + 5_000, clock.elapsedRealtimeAtUtc(time.trueUtc() + 5_000))
        }

    @Test
    fun concurrentSyncsShareOneMeasurement() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            val clock = TrueTime(time)

            val first = async { clock.sync() }
            val second = async { clock.sync() }

            assertSame(first.await(), second.await())
            assertEquals(4, time.sntpCalls)
        }

    @Test
    fun sampleTakenWhileTheWallClockWasSetIsDropped() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            // Android corrects the phone's clock (3 s fast) in the middle of the first exchange.
            time.duringSntp = { call -> if (call == 1) time.systemSkewMillis = 0 }
            val clock = TrueTime(time)

            val sync = clock.sync()!!

            assertEquals(0L, sync.offsetMillis)
            assertEquals(time.trueUtc(), clock.nowUtcMillis())
            assertEquals(5, time.sntpCalls)
        }

    @Test
    fun implausibleFallbackNeverReplacesAGoodClock() =
        runTest {
            val time = FakeTimeSources(testScheduler)
            val clock = TrueTime(time)
            clock.sync()
            delay(45 * 60_000L)
            // Much later SNTP fails, there is no network time and the phone's clock reads 1970.
            time.sntpScript = ArrayDeque()
            time.networkSkewMillis = null
            time.systemSkewMillis = -BASE_UTC

            assertNull(clock.sync())
            assertEquals(time.trueUtc(), clock.nowUtcMillis())
        }
}
