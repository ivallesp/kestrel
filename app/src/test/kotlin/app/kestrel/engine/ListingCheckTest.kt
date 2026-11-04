package app.kestrel.engine

import app.kestrel.model.ItemRef
import app.kestrel.model.PageKind
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingCheckTest {
    private class CheckHarness(
        scope: TestScope,
    ) {
        val store = FakeStore()
        val notifier = FakeNotifier()
        val auction = FakeAuction(scope.testScheduler)
        val page = ScriptedBidPage(scope.testScheduler, auction)
        val check = ListingCheck(store, notifier, VirtualClock(scope.testScheduler), TEST_MINIMUM_RULE)

        init {
            // A day before the end.
            auction.endUtc = BASE_UTC + 86_400_000
            store.put(testSnipe(endUtc = BASE_UTC + 86_400_000))
            page.submitError = AssertionError("a check must never submit")
        }

        suspend fun run(): CheckOutcome = check.check(1, page)

        fun snipe() = store.snipes.getValue(1)
    }

    @Test
    fun aBrowserCheckIsWaitedOutInPlaceAndTheListingStillUpdated() =
        runTest {
            val harness = CheckHarness(this)
            harness.page.pageKinds += PageKind.CHALLENGE
            harness.auction.price = gbp("24.50")
            // The check clears after a few seconds and the item page appears.
            var reads = 0
            harness.page.readListingResult = { if (++reads < 3) null else harness.auction.listing() }

            val outcome = harness.run()

            assertTrue(outcome.toString(), outcome is CheckOutcome.Updated)
            assertEquals(gbp("24.50"), harness.snipe().lastPrice)
            assertEquals("no reload, which would start the check again", 1, harness.page.openItemAtUtc.size)
        }

    @Test
    fun checkUpdatesTheSnipeAndRecordsThePrice() =
        runTest {
            val harness = CheckHarness(this)
            harness.auction.price = gbp("24.50")
            harness.auction.bidCount = 5

            val outcome = harness.run()

            assertTrue(outcome is CheckOutcome.Updated)
            val snipe = harness.snipe()
            assertEquals(gbp("24.50"), snipe.lastPrice)
            assertEquals(5, snipe.lastBidCount)
            assertEquals(gbp("3.20"), snipe.postage)
            assertEquals(BASE_UTC + 1_500, snipe.lastCheckedAtUtcMillis)
            assertEquals(SnipeStatus.ARMED, snipe.status)
            assertEquals(
                gbp("24.50"),
                harness.store.prices
                    .single()
                    .price,
            )
            assertEquals(1, harness.store.eventsOf(TimelineKind.CHECKED).size)
            assertTrue(harness.notifier.priceAlerts.isEmpty())
        }

    @Test
    fun priceOverMaxIsAlertedOncePerPriceLevel() =
        runTest {
            val harness = CheckHarness(this)
            harness.auction.price = gbp("50.00")

            harness.run()
            harness.run()
            harness.auction.price = gbp("55.00")
            harness.run()

            assertEquals(SnipeStatus.PRICE_OVER_MAX, harness.snipe().status)
            assertEquals(
                listOf(gbp("50.00") to gbp("51.00"), gbp("55.00") to gbp("56.00")),
                harness.notifier.priceAlerts,
            )
        }

    @Test
    fun raisedMaxReturnsPriceOverMaxToArmed() =
        runTest {
            val harness = CheckHarness(this)
            harness.auction.price = gbp("50.00")
            harness.run()
            harness.store.edit(1) { it.copy(maxBid = gbp("70.00")) }

            harness.run()

            assertEquals(SnipeStatus.ARMED, harness.snipe().status)
            assertEquals(1, harness.notifier.priceAlerts.size)
        }

    @Test
    fun signInPageNeedsTheUserOnce() =
        runTest {
            val harness = CheckHarness(this)
            harness.page.pageKinds += PageKind.SIGN_IN
            harness.page.pageKinds += PageKind.SIGN_IN

            val first = harness.run()
            harness.run()

            assertEquals(CheckOutcome.NeedsUser(NeedsUserReason.SIGN_IN), first)
            assertEquals(SnipeStatus.NEEDS_SIGN_IN, harness.snipe().status)
            assertEquals(listOf(NeedsUserReason.SIGN_IN), harness.notifier.userAlerts.map { it.first })
        }

    @Test
    fun signedOutItemPageAsksForSignIn() =
        runTest {
            val harness = CheckHarness(this)
            harness.auction.signedIn = false

            harness.run()

            assertEquals(SnipeStatus.NEEDS_SIGN_IN, harness.snipe().status)
            assertEquals(
                NeedsUserReason.SIGN_IN,
                harness.notifier.userAlerts
                    .single()
                    .first,
            )
        }

    @Test
    fun captchaNeedsTheUserAndClearsWhenThePageIsNormalAgain() =
        runTest {
            val harness = CheckHarness(this)
            harness.page.pageKinds += PageKind.CAPTCHA

            assertEquals(CheckOutcome.NeedsUser(NeedsUserReason.CAPTCHA), harness.run())
            assertEquals(SnipeStatus.NEEDS_YOU, harness.snipe().status)
            harness.run()
            assertEquals(SnipeStatus.ARMED, harness.snipe().status)
        }

    @Test
    fun rateWallFailsWithARetryHintAndCountsAsACheck() =
        runTest {
            val harness = CheckHarness(this)
            harness.page.pageKinds += PageKind.RATE_WALL

            val outcome = harness.run()

            assertTrue((outcome as CheckOutcome.Failed).reason.contains("try again"))
            assertEquals(BASE_UTC + 1_500, harness.snipe().lastCheckedAtUtcMillis)
            assertEquals(SnipeStatus.ARMED, harness.snipe().status)
            // The failed attempt spaces the next one out instead of retrying at once.
            val next =
                MonitorPolicy.nextCheckAt(
                    BASE_UTC + 1_500,
                    harness.snipe().endTimeUtcMillis,
                    harness.snipe().lastCheckedAtUtcMillis,
                )
            assertTrue(next!! > BASE_UTC + 3_600_000)
        }

    @Test
    fun listingEndedEarlyEndsTheSnipe() =
        runTest {
            val harness = CheckHarness(this)
            harness.auction.endedEarly = true

            val outcome = harness.run()

            assertTrue(outcome is CheckOutcome.Ended)
            assertEquals(SnipeStatus.ENDED_EARLY, harness.snipe().status)
            assertEquals(
                SnipeOutcome.ENDED_EARLY,
                harness.notifier.results
                    .single()
                    .outcome,
            )
        }

    @Test
    fun relistedListingIsNotMistakenForTheOriginal() =
        runTest {
            val harness = CheckHarness(this)
            harness.page.readListingResult = { null }
            val relisted =
                testListing(endUtc = BASE_UTC + 7 * 86_400_000L, price = gbp("9.99"), relistedItemId = "127999999999")
            val page =
                object : BidLayerPreview by harness.page {
                    override suspend fun openItem(ref: ItemRef) =
                        PageState(PageKind.NORMAL, ref.url, "Camera | eBay", relisted)
                }

            val outcome = harness.check.check(1, page)

            assertTrue(outcome is CheckOutcome.Ended)
            assertEquals(SnipeStatus.ENDED_EARLY, harness.snipe().status)
            // Nothing of the other listing was copied in.
            assertEquals(gbp("20.00"), harness.snipe().lastPrice)
            assertEquals(BASE_UTC + 86_400_000, harness.snipe().endTimeUtcMillis)
        }

    @Test
    fun buyItNowAppearingIsWrittenToTheTimeline() =
        runTest {
            val harness = CheckHarness(this)
            val withBuyItNow = testListing(endUtc = BASE_UTC + 86_400_000, buyItNow = gbp("80.00"))
            val page =
                object : BidLayerPreview by harness.page {
                    override suspend fun openItem(ref: ItemRef) =
                        PageState(PageKind.NORMAL, ref.url, "Camera | eBay", withBuyItNow)
                }

            harness.check.check(1, page)

            assertEquals(gbp("80.00"), harness.snipe().buyItNow)
            assertTrue(
                harness.store
                    .eventsOf(TimelineKind.CHECKED)
                    .single()
                    .message
                    .contains("Buy It Now"),
            )
        }

    @Test
    fun runningWindowIsLeftAlone() =
        runTest {
            val harness = CheckHarness(this)
            harness.store.edit(1) { it.copy(status = SnipeStatus.BID_SENT) }

            val outcome = harness.run()

            assertTrue(outcome is CheckOutcome.Failed)
            assertTrue(harness.page.openItemAtUtc.isEmpty())
        }

    @Test
    fun abandonedWindowIsResolvedAfterTheEnd() =
        runTest {
            val harness = CheckHarness(this)
            harness.auction.endUtc = BASE_UTC - 3_600_000
            harness.auction.finalPage = FinalPage(price = gbp("30.00"), userIsHighBidder = true)
            harness.store.edit(1) { it.copy(status = SnipeStatus.LEADING, endTimeUtcMillis = BASE_UTC - 3_600_000) }

            val outcome = harness.run()

            assertTrue(outcome is CheckOutcome.Ended)
            assertEquals(SnipeStatus.WON, harness.snipe().status)
        }

    @Test
    fun aWindowThisProcessDoesNotRunIsResolvedAtTheFirstCheckAfterTheEnd() =
        runTest {
            val harness = CheckHarness(this)
            // The process died after the bid; the end was three minutes ago and no window runs in this process.
            harness.auction.endUtc = BASE_UTC - 180_000
            harness.auction.finalPage = FinalPage(price = gbp("30.00"), userIsHighBidder = true)
            harness.store.edit(1) { it.copy(status = SnipeStatus.LEADING, endTimeUtcMillis = BASE_UTC - 180_000) }
            val check =
                ListingCheck(
                    harness.store,
                    harness.notifier,
                    VirtualClock(testScheduler),
                    TEST_MINIMUM_RULE,
                    windowRunning = { false },
                )

            val outcome = check.check(1, harness.page)

            assertTrue(outcome.toString(), outcome is CheckOutcome.Ended)
            assertEquals(SnipeStatus.WON, harness.snipe().status)
        }

    @Test
    fun watchOnlySnipeIsUpdatedWithoutAlerts() =
        runTest {
            val harness = CheckHarness(this)
            harness.store.edit(1) { it.copy(maxBid = null, status = SnipeStatus.WATCHING) }
            harness.auction.price = gbp("500.00")
            harness.auction.signedIn = false

            harness.run()

            assertEquals(SnipeStatus.WATCHING, harness.snipe().status)
            assertTrue(harness.notifier.priceAlerts.isEmpty())
            assertTrue(harness.notifier.userAlerts.isEmpty())
            assertNull(harness.snipe().result)
        }

    @Test
    fun checkFinishingAfterTheWindowStartedLeavesTheWindowAlone() =
        runTest {
            val harness = CheckHarness(this)
            harness.auction.price = gbp("50.00")
            val page =
                object : BidLayerPreview by harness.page {
                    override suspend fun openItem(ref: ItemRef): PageState {
                        val state = harness.page.openItem(ref)
                        // The bid window opens while the check is still loading the page.
                        harness.store.edit(1) { it.copy(status = SnipeStatus.BIDDING) }
                        return state
                    }
                }

            val outcome = harness.check.check(1, page)

            assertTrue(outcome is CheckOutcome.Failed)
            assertEquals(SnipeStatus.BIDDING, harness.snipe().status)
            assertTrue(harness.notifier.priceAlerts.isEmpty())
        }
}
