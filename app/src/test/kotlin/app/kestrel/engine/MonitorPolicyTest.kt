package app.kestrel.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorPolicyTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val end = BASE_UTC + 30 * 24 * hour

    private fun intervalAfterCheckAt(timeLeft: Long): Long? {
        val last = end - timeLeft
        return MonitorPolicy.nextCheckAt(last, end, last)?.minus(last)
    }

    private fun assertWithinTenPercent(
        expected: Long,
        actual: Long?,
    ) {
        assertTrue(
            "got $actual for $expected",
            actual != null && actual in (expected * 0.9).toLong()..(expected * 1.1).toLong(),
        )
    }

    @Test
    fun cadenceTable() {
        // Time left at the last check -> about how long until the next one.
        assertWithinTenPercent(6 * hour, intervalAfterCheckAt(3 * 24 * hour))
        assertWithinTenPercent(6 * hour, intervalAfterCheckAt(31 * hour))
        assertWithinTenPercent(2 * hour, intervalAfterCheckAt(20 * hour))
        assertWithinTenPercent(2 * hour, intervalAfterCheckAt(9 * hour))
        assertWithinTenPercent(hour, intervalAfterCheckAt(5 * hour))
        assertWithinTenPercent(hour, intervalAfterCheckAt(3 * hour))
        assertWithinTenPercent(15 * minute, intervalAfterCheckAt(50 * minute))
        assertWithinTenPercent(15 * minute, intervalAfterCheckAt(30 * minute))
        // In the last ten minutes the dry run and the window take over.
        assertNull(intervalAfterCheckAt(9 * minute))
        assertNull(intervalAfterCheckAt(3 * minute))
    }

    @Test
    fun checksNeverJumpOverAFasterBand() {
        // An hourly check 61 minutes out must not skip the 15-minute band.
        val next = MonitorPolicy.nextCheckAt(end - 61 * minute, end, end - 61 * minute)!!
        assertTrue(next in end - 48 * minute..end - 44 * minute)
        // A 6-hourly check 25 hours out comes back about two hours later, inside the 2-hour band.
        val later = MonitorPolicy.nextCheckAt(end - 25 * hour, end, end - 25 * hour)!!
        assertTrue(later in end - 24 * hour + 1..end - 22 * hour)
    }

    @Test
    fun noCheckFallsAfterTheWindowOpens() {
        assertNull(MonitorPolicy.nextCheckAt(end - 15 * minute, end, end - 15 * minute))
        assertNull(MonitorPolicy.nextCheckAt(end - 60_000, end, end - 3 * hour))
        val near = MonitorPolicy.nextCheckAt(end - 25 * minute, end, end - 25 * minute)
        assertTrue(near == null || near < MonitorPolicy.windowOpensAt(end))
    }

    @Test
    fun firstAndOverdueChecksAreDueNow() {
        val now = end - 10 * hour
        assertEquals(now, MonitorPolicy.nextCheckAt(now, end, null))
        assertEquals(now, MonitorPolicy.nextCheckAt(now, end, now - 3 * hour))
    }

    @Test
    fun jitterIsDeterministicAndWithinTenPercent() {
        val intervals =
            (0 until 200).map { step ->
                val last = end - 3 * 24 * hour + step * 7_919L
                val first = MonitorPolicy.nextCheckAt(last, end, last)!!
                assertEquals(first, MonitorPolicy.nextCheckAt(last, end, last))
                first - last
            }
        assertTrue(intervals.all { it in (5.4 * hour).toLong()..(6.6 * hour).toLong() })
        assertTrue("jitter should vary", intervals.toSet().size > 150)
    }

    @Test
    fun windowAndDryRunTimes() {
        assertEquals(end - 10 * minute, MonitorPolicy.dryRunAt(end))
        assertEquals(end - 120_000, MonitorPolicy.windowOpensAt(end))
        assertEquals(end - 75_000, MonitorPolicy.backupWindowAt(end))
    }

    @Test
    fun editsLockTwoSecondsBeforeTheBid() {
        assertEquals(end - 6_000, BidTiming.fireAtUtcMillis(end, 6))
        assertEquals(end - 8_000, BidTiming.editsLockAtUtcMillis(end, 6))
        // Out-of-range leads are clamped instead of bidding at or after the end.
        assertEquals(end - 1_000, BidTiming.fireAtUtcMillis(end, 0))
        assertEquals(end - 90_000, BidTiming.fireAtUtcMillis(end, 600))
    }
}
