package app.kestrel.web

import app.kestrel.engine.BidPageFactory
import app.kestrel.engine.END_UTC
import app.kestrel.engine.FakeNotifier
import app.kestrel.engine.FakeStore
import app.kestrel.engine.FakeTimeSources
import app.kestrel.engine.NeedsUserReason
import app.kestrel.engine.SnipeEngine
import app.kestrel.engine.TEST_MINIMUM_RULE
import app.kestrel.engine.TrueTime
import app.kestrel.engine.testSnipe
import app.kestrel.model.AutoRefreshState
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real engine window driving the real [WebBidPage] over the simulated eBay page, on virtual time. The engine's
 * own fake page answers whatever a test scripts; this rig keeps the web layer's rules (one press per page, verified
 * typing, kestrel.js refusals) in the loop.
 */
class EngineOnWebPageTest {
    private class Rig(
        scope: TestScope,
    ) {
        val store = FakeStore()
        val notifier = FakeNotifier()
        val time = FakeTimeSources(scope.testScheduler)
        val clock = TrueTime(time)
        val driver = FakePageDriver()
        val ebay = SimulatedEbayPage(driver)
        val parsers = FakeParsers()
        val price = Money.of("5.37", "GBP")
        val max = Money.of("12.50", "GBP")
        var finalPrice = Money.of("9.00", "GBP")
        var pagesCreated = 0
        val engine =
            SnipeEngine(
                store,
                notifier,
                clock,
                BidPageFactory {
                    pagesCreated++
                    WebBidPage(driver, PageScript("var VERSION = '1';"), parsers, clock)
                },
                TEST_MINIMUM_RULE,
            )

        init {
            store.put(testSnipe(max = max, lastPrice = price, lastBidCount = 1))
            parsers.listingFor = { _, ref, at ->
                val ended = time.trueUtc() >= END_UTC
                sampleListing(ref, fetchedAtUtcMillis = at).copy(
                    endTimeUtcMillis = END_UTC,
                    status = if (ended) ListingStatus.ENDED else ListingStatus.ACTIVE,
                    currentPrice = if (ended) finalPrice else price,
                    bidCount = if (ended) 4 else 1,
                    userIsHighBidder = null,
                    signedIn = null,
                )
            }
            parsers.autoRefreshState = AutoRefreshState(price, 1, END_UTC, 2, false)
            ebay.clock = { time.trueUtc() }
        }

        suspend fun run(): SnipeResult = engine.runWindow(1)

        fun snipe() = store.snipes.getValue(1)
    }

    @Test
    fun aBidButtonThatEnablesLateAfterTypingStillBids() =
        runTest {
            val rig = Rig(this)
            // eBay checks the amount on its server and enables its bid button 3 s after typing.
            rig.ebay.submitEnableDelayMillis = 3_000

            val result = rig.run()

            assertEquals(1, rig.ebay.presses)
            assertEquals(6_000L, result.bidLeadMillis)
            assertEquals(1, rig.store.eventsOf(TimelineKind.BID_SENT).size)
        }

    @Test
    fun aCheckShownAfterTypingIsHandedToTheUserAtOnce() =
        runTest {
            val rig = Rig(this)
            rig.ebay.interstitialAfterTyping = "verification" to "Verify your phone number to bid"

            val result = rig.run()

            assertEquals(0, rig.ebay.presses)
            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertEquals(
                NeedsUserReason.VERIFICATION,
                rig.notifier.userAlerts
                    .single()
                    .first,
            )
            assertTrue(result.reason!!, result.reason!!.contains("Verify your phone number to bid"))
            // Handed over right after typing at T-45 s, not at the fire time.
            val handOff = rig.store.eventsOf(TimelineKind.NEEDS_USER).single()
            assertTrue(handOff.trueUtcMillis < END_UTC - 30_000)
        }

    @Test
    fun aPressThePageRefusedIsNeitherABidNorALoss() =
        runTest {
            val rig = Rig(this)
            rig.ebay.touchReachesPage = false
            // kestrel.js refuses the press: a dialog lies over eBay's bid button, so nothing is clicked.
            rig.ebay.submitRefusalOverride = "covered"

            val result = rig.run()

            assertEquals(0, rig.ebay.presses)
            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertEquals(SnipeStatus.FAILED, rig.snipe().status)
            assertNull(result.bidSentAtUtcMillis)
            assertNull(result.bidLeadMillis)
            assertTrue(rig.store.eventsOf(TimelineKind.BID_SENT).isEmpty())
            assertTrue(result.reason!!, result.reason!!.contains("another dialog lies over eBay's bid button"))
            assertEquals(
                NeedsUserReason.VERIFICATION,
                rig.notifier.userAlerts
                    .single()
                    .first,
            )
        }
}
