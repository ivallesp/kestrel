package app.kestrel.ui.screens.lot

import app.kestrel.engine.BidPageFactory
import app.kestrel.engine.BidReply
import app.kestrel.engine.END_UTC
import app.kestrel.engine.FakeAuction
import app.kestrel.engine.FakeNotifier
import app.kestrel.engine.FakeStore
import app.kestrel.engine.FakeTimeSources
import app.kestrel.engine.FinalPage
import app.kestrel.engine.NeedsUserReason
import app.kestrel.engine.ScriptedBidPage
import app.kestrel.engine.SnipeEngine
import app.kestrel.engine.TEST_MINIMUM_RULE
import app.kestrel.engine.TrueTime
import app.kestrel.engine.gbp
import app.kestrel.engine.testSnipe
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * A real bid window on the scripted page, then the lot screens built from exactly what it stored (snipe and
 * timeline): the words S3 and S4 show must say what happened on eBay.
 */
class WindowToLotWordsTest {
    private class Harness(
        scope: TestScope,
    ) {
        val store = FakeStore()
        val time = FakeTimeSources(scope.testScheduler)
        val auction = FakeAuction(scope.testScheduler)
        val page = ScriptedBidPage(scope.testScheduler, auction)
        val engine = SnipeEngine(store, FakeNotifier(), TrueTime(time), BidPageFactory { page }, TEST_MINIMUM_RULE)

        init {
            store.put(testSnipe())
        }

        private fun events(): List<TimelineEvent> =
            store.events.mapIndexed { index, event ->
                TimelineEvent(
                    index.toLong(),
                    event.snipeId,
                    event.trueUtcMillis,
                    event.kind,
                    event.message,
                    event.detail,
                )
            }

        fun inputs(): LotInputs =
            LotInputs(store.snipes.getValue(1), events(), emptyList(), Instant.ofEpochMilli(time.trueUtc()))
    }

    @Test
    fun aBidThatDidNotRegisterGivesKestrelsReasonNotTheEndBannerAsARefusal() =
        runTest {
            val harness = Harness(this)
            harness.auction.bidCount = 0
            harness.store.edit(1) { it.copy(lastBidCount = 0) }
            harness.auction.finalPage =
                FinalPage(price = gbp("20.00"), bidCount = 0, endedText = "Bidding ended on Wed, 4 Nov at 1:48 PM.")

            harness.engine.runWindow(1)

            val snipe = harness.store.snipes.getValue(1)
            assertEquals(SnipeOutcome.FAILED, snipe.result?.outcome)
            assertNull("the end banner is not eBay's reply to the bid", snipe.result?.ebayReplyText)
            val result = (LotPageBuilder.page(harness.inputs()) as LotPage.Finished).state
            val reason = "eBay shows no bids on the listing, so the bid did not register."
            assertEquals(reason, snipe.result?.reason)
            assertEquals(reason, result.outcome?.text)
            assertTrue(
                result.receipt.toString(),
                result.receipt.any { it.title == "Bid failed" && it.detail == reason },
            )
            assertFalse(result.receipt.any { it.detail?.contains("eBay refused it") == true })
        }
}
