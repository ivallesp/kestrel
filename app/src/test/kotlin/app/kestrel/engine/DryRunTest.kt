package app.kestrel.engine

import app.kestrel.model.PageKind
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DryRunTest {
    private class DryRunHarness(
        scope: TestScope,
    ) {
        val store = FakeStore()
        val notifier = FakeNotifier()
        val auction = FakeAuction(scope.testScheduler)
        val page = ScriptedBidPage(scope.testScheduler, auction)
        val dryRun = DryRun(store, notifier, VirtualClock(scope.testScheduler), TEST_MINIMUM_RULE)

        init {
            // Ten minutes before the end; a dry run never submits, whatever happens.
            auction.endUtc = BASE_UTC + 600_000
            store.put(testSnipe(endUtc = BASE_UTC + 600_000))
            page.submitError = AssertionError("a dry run must never submit")
        }

        suspend fun run(): DryRunOutcome = dryRun.run(1, page)

        fun snipe() = store.snipes.getValue(1)
    }

    @Test
    fun dryRunTypesTheMaxAndNeverSubmits() =
        runTest {
            val harness = DryRunHarness(this)
            harness.page.typedMinimum = { gbp("21.00") }

            val outcome = harness.run()

            assertEquals(DryRunOutcome.Passed, outcome)
            assertTrue(harness.page.submitsAtUtc.isEmpty())
            assertEquals(listOf(gbp("48.00")), harness.page.typed)
            assertEquals(1, harness.page.layerCloses)
            assertEquals(true, harness.snipe().dryRunPassed)
            assertEquals(SnipeStatus.ARMED, harness.snipe().status)
            val event = harness.store.eventsOf(TimelineKind.DRY_RUN).single()
            assertTrue(event.message.contains("No bid was placed"))
            assertTrue(event.detail!!.contains(".placebid-redesign-wrapper"))
        }

    @Test
    fun aBidLayerThatNeverAnswersStillLeavesThePageReport() =
        runTest {
            val harness = DryRunHarness(this)
            // The page keeps waiting for eBay's scripts longer than the dry run waits for the page.
            harness.page.layerOpenMillis = 60_000

            val outcome = harness.run()

            assertTrue(outcome is DryRunOutcome.Failed)
            val event = harness.store.eventsOf(TimelineKind.DRY_RUN).single()
            assertTrue(event.detail!!, event.detail!!.contains("Vintage camera | eBay"))
        }

    @Test
    fun verificationPromptFailsTheDryRunWithAClearReason() =
        runTest {
            val harness = DryRunHarness(this)
            harness.page.layerState =
                { LayerState.NeedsUser("Verify your phone number to bid", "layer: phone verification") }

            val outcome = harness.run()

            assertTrue(outcome is DryRunOutcome.Failed)
            assertEquals(NeedsUserReason.VERIFICATION, (outcome as DryRunOutcome.Failed).reason)
            assertEquals(SnipeStatus.DRY_RUN_FAILED, harness.snipe().status)
            assertEquals(false, harness.snipe().dryRunPassed)
            assertEquals(
                NeedsUserReason.VERIFICATION,
                harness.notifier.userAlerts
                    .single()
                    .first,
            )
            assertTrue(
                harness.notifier.userAlerts
                    .single()
                    .second!!
                    .contains("Verify your phone number"),
            )
            assertEquals(1, harness.page.layerCloses)
            assertTrue(harness.page.typed.isEmpty())
        }

    @Test
    fun signInPageFailsTheDryRunWithTheSignInReason() =
        runTest {
            val harness = DryRunHarness(this)
            harness.page.pageKinds += PageKind.SIGN_IN

            val outcome = harness.run()

            assertEquals(NeedsUserReason.SIGN_IN, (outcome as DryRunOutcome.Failed).reason)
            assertEquals(SnipeStatus.DRY_RUN_FAILED, harness.snipe().status)
            assertEquals(0, harness.page.layerOpens)
        }

    @Test
    fun fieldShowingAnotherAmountFailsTheDryRun() =
        runTest {
            val harness = DryRunHarness(this)
            harness.page.fieldValueFor = { "4" }

            val outcome = harness.run()

            assertEquals(NeedsUserReason.DRY_RUN_FAILED, (outcome as DryRunOutcome.Failed).reason)
            assertEquals(1, harness.page.layerCloses)
        }

    @Test
    fun minimumAboveTheMaxPassesButRaisesThePriceAlert() =
        runTest {
            val harness = DryRunHarness(this)
            harness.page.typedMinimum = { gbp("52.00") }
            harness.page.typedInlineError = { "Enter £52.00 or more" }

            val outcome = harness.run()

            assertEquals(DryRunOutcome.Passed, outcome)
            assertEquals(SnipeStatus.PRICE_OVER_MAX, harness.snipe().status)
            assertEquals(
                gbp("52.00"),
                harness.notifier.priceAlerts
                    .single()
                    .second,
            )
        }

    @Test
    fun passingDryRunClearsAnEarlierFailure() =
        runTest {
            val harness = DryRunHarness(this)
            harness.store.edit(1) { it.copy(status = SnipeStatus.DRY_RUN_FAILED, dryRunPassed = false) }

            harness.run()

            assertEquals(SnipeStatus.ARMED, harness.snipe().status)
            assertEquals(true, harness.snipe().dryRunPassed)
            // The pass proves the earlier problem over, so its alert is taken back.
            assertEquals(listOf(1L), harness.notifier.needsUserCleared)
        }

    @Test
    fun watchOnlySnipeIsSkipped() =
        runTest {
            val harness = DryRunHarness(this)
            harness.store.edit(1) { it.copy(maxBid = null, status = SnipeStatus.WATCHING) }

            assertTrue(harness.run() is DryRunOutcome.Skipped)
            assertTrue(harness.page.openItemAtUtc.isEmpty())
        }

    @Test
    fun rateWallIsRetriedOnceAfterABackoff() =
        runTest {
            val harness = DryRunHarness(this)
            harness.page.pageKinds += PageKind.RATE_WALL

            val outcome = harness.run()

            assertEquals(DryRunOutcome.Passed, outcome)
            val gap = harness.page.openItemAtUtc[1] - harness.page.openItemAtUtc[0]
            assertTrue("gap $gap", gap in 9_500L..13_600L)
        }

    @Test
    fun dryRunFinishingAfterTheWindowStartedLeavesTheWindowAlone() =
        runTest {
            val harness = DryRunHarness(this)
            harness.page.layerState = {
                // The bid window opens while the dry run is still in the layer.
                harness.store.edit(1) { it.copy(status = SnipeStatus.BIDDING) }
                LayerState.Ready(minimum = null, submitLabel = "Submit bid", report = "layer")
            }

            val outcome = harness.run()

            assertTrue(outcome is DryRunOutcome.Skipped)
            assertEquals(SnipeStatus.BIDDING, harness.snipe().status)
            assertEquals(1, harness.page.layerCloses)
        }
}
