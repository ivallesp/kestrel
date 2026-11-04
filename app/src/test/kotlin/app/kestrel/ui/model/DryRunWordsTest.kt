package app.kestrel.ui.model

import app.kestrel.data.MINUTE
import app.kestrel.data.TEST_NOW
import app.kestrel.data.testSnipe
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.model.TimelineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/** A failed dry run is told in the engine's own words, and one about the max is fixed by changing the max. */
class DryRunWordsTest {
    private val now = Instant.ofEpochMilli(TEST_NOW)
    private val snipe =
        testSnipe(status = SnipeStatus.DRY_RUN_FAILED, endTimeUtcMillis = TEST_NOW + 8 * MINUTE)
            .copy(dryRunAtUtcMillis = TEST_NOW - MINUTE, dryRunPassed = false)

    private fun statusAfter(
        message: String,
        detail: String,
    ): LotStatus {
        val event = TimelineEvent(1, snipe.id, TEST_NOW - MINUTE, TimelineKind.DRY_RUN, message, detail)
        return LotStatusMapper.statusOf(snipe, listOf(event), now, Connectivity.Online)
    }

    @Test
    fun aDryRunThatReachedThePanelSaysWhatWentWrongThere() {
        val status =
            statusAfter(
                "Dry run failed: eBay's bid field showed \"4\" instead of your max £48.00. No bid was placed.",
                """{"reason":"DRY_RUN_FAILED"}""",
            )

        val detail = status.detail(now, ZoneOffset.UTC)
        assertFalse(detail, detail.contains("didn’t reach the bid panel"))
        assertTrue(detail, detail.contains("eBay’s bid field showed “4” instead of your max £48.00."))
        assertEquals(LotAction.FIX, status.action(now))
    }

    @Test
    fun aDryRunThatFailedOnTheMaxIsFixedByEditingIt() {
        val status =
            statusAfter(
                "Dry run failed: Your max is in GBP, but this listing takes bids in EUR. No bid was placed.",
                """{"reason":"BID_FAILED","aboutTheMax":true}""",
            )

        val detail = status.detail(now, ZoneOffset.UTC)
        assertTrue(detail, detail.contains("your max is in GBP, but this listing takes bids in EUR."))
        assertEquals(LotAction.EDIT_MAX, status.action(now))
    }
}
