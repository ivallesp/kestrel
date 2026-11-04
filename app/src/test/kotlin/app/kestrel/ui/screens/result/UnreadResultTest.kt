package app.kestrel.ui.screens.result

import app.kestrel.data.HOUR
import app.kestrel.data.TEST_NOW
import app.kestrel.data.gbp
import app.kestrel.data.testSnipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.model.TimelineKind
import app.kestrel.platform.SnipeJobAftermath
import app.kestrel.ui.screens.lot.LotInputs
import app.kestrel.ui.screens.lot.LotPage
import app.kestrel.ui.screens.lot.LotPageBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * A sent bid whose result Kestrel never read, once the checks gave up three days after the end: the alert asks the
 * user to mark it won or lost, so S4 must offer that, and let it be deleted, and stop saying it keeps checking.
 */
class UnreadResultTest {
    private val end = TEST_NOW - 4 * 24 * HOUR
    private val now = Instant.ofEpochMilli(TEST_NOW)

    private fun event(
        id: Long,
        at: Long,
        kind: TimelineKind,
        message: String,
        detail: String? = null,
    ) = TimelineEvent(id, 1, at, kind, message, detail)

    private val bidSent =
        event(
            1,
            end - 6_000,
            TimelineKind.BID_SENT,
            "Bid of £48.00 sent 6.00 s before the end.",
            """{"sentAtUtcMillis":${end - 6_000},"amount":"GBP 48.00"}""",
        )
    private val gaveUp = event(9, end + 3 * 24 * HOUR, TimelineKind.PROBLEM, SnipeJobAftermath.RESULT_UNREAD)

    @Test
    fun anUnreadResultCanBeMarkedOrDeletedAndNoLongerClaimsKestrelIsChecking() {
        val unknown = SnipeResult(SnipeOutcome.UNKNOWN, gbp("40.00"), end - 6_000, 6_000, null, null, "unread")
        val snipe = testSnipe(status = SnipeStatus.BID_SENT, endTimeUtcMillis = end).copy(result = unknown)
        val events =
            listOf(
                bidSent,
                event(
                    2,
                    end + 60_000,
                    TimelineKind.RESULT,
                    "Kestrel could not read the final result from eBay. Check the listing on eBay.",
                    """{"outcome":"UNKNOWN"}""",
                ),
                gaveUp,
            )

        val result = (LotPageBuilder.page(LotInputs(snipe, events, emptyList(), now)) as LotPage.Finished).state

        assertTrue("Mark as won or lost", result.canSettle)
        assertTrue("Delete", result.canDelete)
        assertFalse(result.outcome!!.text, result.outcome!!.text.contains("keeps checking"))
        assertFalse(result.receipt.toString(), result.receipt.any { it.detail?.contains("keeps checking") == true })
    }

    @Test
    fun withoutAWindowResultTheLotStopsSayingItIsStillReading() {
        val snipe = testSnipe(status = SnipeStatus.LEADING, endTimeUtcMillis = end)

        val page = LotPageBuilder.page(LotInputs(snipe, listOf(bidSent, gaveUp), emptyList(), now))

        val result = (page as LotPage.Finished).state
        assertTrue(result.canSettle)
        assertTrue(result.canDelete)
    }
}
