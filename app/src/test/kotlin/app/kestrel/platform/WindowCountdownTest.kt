package app.kestrel.platform

import app.kestrel.engine.WindowPhase
import app.kestrel.model.SnipeOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the Live Update counts down to, around the bid and the end, to the millisecond. */
class WindowCountdownTest {
    private val bid = 1_000_000L
    private val end = bid + 6_000L

    private fun target(
        phase: WindowPhase?,
        now: Long,
        endAt: Long = end,
    ): Long? =
        WindowCountdown.target(phase, outcome = null, bidAtUtcMillis = bid, endUtcMillis = endAt, nowUtcMillis = now)

    @Test
    fun beforeTheBidItCountsToTheBid() {
        assertEquals(bid, target(WindowPhase.CHECKING, now = bid - 60_000))
        assertEquals(bid, target(WindowPhase.READY, now = bid - 1))
    }

    @Test
    fun atTheBidMomentItCountsToTheEndEvenBeforeTheEngineMovesOn() {
        assertEquals(end, target(WindowPhase.READY, now = bid))
        assertEquals(end, target(WindowPhase.BIDDING, now = bid + 300))
    }

    @Test
    fun afterTheBidItFollowsTheEndThroughExtensions() {
        assertEquals(end, target(WindowPhase.BID_SENT, now = bid + 1))
        assertEquals(end + 60_000, target(WindowPhase.EXTENDED, now = end + 1_000, endAt = end + 60_000))
    }

    @Test
    fun atTheEndAndAfterItThereIsNoCountdown() {
        assertNull(target(WindowPhase.LEADING, now = end))
        assertNull(target(WindowPhase.RESOLVING, now = end + 5_000))
        assertNull(target(WindowPhase.READY, now = end + 1))
    }

    @Test
    fun aKnownOutcomeOrAnUnknownBidMomentHasNoCountdown() {
        assertNull(
            WindowCountdown.target(
                WindowPhase.READY,
                null,
                bidAtUtcMillis = null,
                endUtcMillis = end,
                nowUtcMillis = 0,
            ),
        )
        assertNull(
            WindowCountdown.target(
                WindowPhase.LEADING,
                outcome = SnipeOutcome.WON,
                bidAtUtcMillis = bid,
                endUtcMillis = end,
                nowUtcMillis = bid - 10,
            ),
        )
    }
}
