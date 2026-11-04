package app.kestrel.engine

import app.kestrel.model.PageKind
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnipeEngineTest {
    private class WindowHarness(
        scope: TestScope,
    ) {
        val store = FakeStore()
        val notifier = FakeNotifier()
        val time = FakeTimeSources(scope.testScheduler)
        val clock = TrueTime(time)
        val auction = FakeAuction(scope.testScheduler)
        val page = ScriptedBidPage(scope.testScheduler, auction)

        /** Pages handed out after the first one, in order; the first page again when none is left. */
        val laterPages = ArrayDeque<ScriptedBidPage>()
        var pagesCreated = 0
        val engine =
            SnipeEngine(
                store,
                notifier,
                clock,
                BidPageFactory {
                    pagesCreated++
                    if (pagesCreated == 1) page else laterPages.removeFirstOrNull() ?: page
                },
                TEST_MINIMUM_RULE,
            )

        init {
            store.put(testSnipe())
        }

        suspend fun run(): SnipeResult = engine.runWindow(1)

        fun snipe() = store.snipes.getValue(1)

        fun forbidSubmit() {
            page.submitError = AssertionError("the window must not submit in this scenario")
        }

        fun winAt(price: String) {
            auction.finalPage = FinalPage(price = gbp(price), bidCount = 5, userIsHighBidder = true)
        }
    }

    @Test
    fun leadingThenWon() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 450)
            harness.winAt("30.00")

            val result = harness.run()

            assertEquals(SnipeOutcome.WON, result.outcome)
            assertEquals(SnipeStatus.WON, harness.snipe().status)
            assertEquals(result, harness.snipe().result)
            assertEquals(listOf(gbp("48.00")), harness.page.typed)
            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(6_000L, result.bidLeadMillis)
            assertEquals(450L, result.replyLatencyMillis)
            assertEquals("Your current bid puts you in the lead.", result.ebayReplyText)
            assertEquals(gbp("30.00"), result.finalPrice)
            assertEquals(listOf(result), harness.notifier.results)
            assertTrue(
                harness.notifier.phases.containsAll(
                    listOf(WindowPhase.READY, WindowPhase.BID_SENT, WindowPhase.LEADING),
                ),
            )
            assertEquals(listOf(1L), harness.notifier.windowsClosed)
            assertTrue(harness.page.closed)
            val kinds = harness.store.events.map { it.kind }
            val expected =
                listOf(
                    TimelineKind.CLOCK_SYNCED,
                    TimelineKind.WINDOW_OPENED,
                    TimelineKind.PAGE_READY,
                    TimelineKind.LAYER_READY,
                    TimelineKind.BID_SENT,
                    TimelineKind.EBAY_REPLY,
                    TimelineKind.ENDED,
                    TimelineKind.RESULT,
                )
            assertTrue(kinds.containsAll(expected))
            assertEquals(expected, expected.sortedBy { kinds.indexOf(it) })
            // Timeline instants are true time, not the phone's clock (which runs 3 s fast here).
            val bidSent = harness.store.eventsOf(TimelineKind.BID_SENT).single()
            assertEquals(END_UTC - 6_000L, bidSent.trueUtcMillis)
        }

    @Test
    fun outbidImmediatelyThenLost() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Outbid("Another bidder is winning. Increase your bid.", 380)
            harness.auction.finalPage = FinalPage(price = gbp("50.00"), bidCount = 7, userIsHighBidder = false)

            val result = harness.run()

            assertEquals(SnipeOutcome.LOST, result.outcome)
            assertEquals(SnipeStatus.LOST, harness.snipe().status)
            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals("Another bidder is winning. Increase your bid.", result.ebayReplyText)
            assertTrue(WindowPhase.OUTBID in harness.notifier.phases)
        }

    @Test
    fun priceThatPassesTheMaxBeforeFiringPlacesNoBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            launch {
                delay(100_000)
                harness.auction.price = gbp("50.00")
                harness.auction.bidCount = 6
            }

            val result = harness.run()

            assertEquals(SnipeOutcome.PRICE_PASSED_MAX, result.outcome)
            assertEquals(SnipeStatus.LOST, harness.snipe().status)
            assertTrue(harness.page.submitsAtUtc.isEmpty())
            assertEquals(1, harness.page.layerCloses)
            assertEquals(gbp("50.00") to gbp("51.00"), harness.notifier.priceAlerts.last())
            assertEquals(listOf(result), harness.notifier.results)
        }

    @Test
    fun maxEditedDuringTheWindowIsTyped() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("49.00")
            launch {
                delay(90_000)
                harness.store.edit(1) { it.copy(maxBid = gbp("55.00")) }
            }

            val result = harness.run()

            assertEquals(listOf(gbp("48.00"), gbp("55.00")), harness.page.typed)
            assertEquals(1, harness.page.submitsAtUtc.size)
            assertTrue(
                harness.store
                    .eventsOf(TimelineKind.BID_SENT)
                    .single()
                    .message
                    .contains("£55.00"),
            )
            assertEquals(1, harness.store.eventsOf(TimelineKind.EDITED).size)
            // £49 is above the old max, so only the edited max explains the win.
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun theRaiseHintCountsDownToTheLockTheUserGetsThreeSecondsBeforeTheBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            launch {
                delay(50_000)
                harness.auction.price = gbp("50.00")
                harness.auction.bidCount = 6
            }

            harness.run()

            val alert = harness.store.eventsOf(TimelineKind.PRICE_OVER_MAX).first()
            // Lead 6 s: the user's edits lock 3 s before the bid, at end - 9 s.
            val untilLock = durationText(END_UTC - 9_000L - alert.trueUtcMillis)
            assertTrue(alert.message, alert.message.endsWith("Raise the max within $untilLock to still bid."))
        }

    @Test
    fun cancelDuringTheWindowPlacesNoBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            launch {
                delay(90_000)
                harness.store.edit(1) { it.copy(status = SnipeStatus.CANCELLED) }
            }

            val result = harness.run()

            assertEquals(SnipeOutcome.CANCELLED, result.outcome)
            assertEquals(SnipeStatus.CANCELLED, harness.snipe().status)
            assertTrue(harness.page.submitsAtUtc.isEmpty())
            assertEquals(1, harness.page.layerCloses)
            assertEquals(1, harness.store.eventsOf(TimelineKind.CANCELLED).size)
        }

    @Test
    fun signInLostAtWindowOpenHandsOverToTheUser() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.page.pageKinds += PageKind.SIGN_IN

            val result = harness.run()

            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertEquals(SnipeStatus.FAILED, harness.snipe().status)
            assertEquals(
                NeedsUserReason.SIGN_IN,
                harness.notifier.userAlerts
                    .single()
                    .first,
            )
            assertTrue(harness.page.typed.isEmpty())
            assertEquals(1, harness.page.openItemAtUtc.size)
            assertEquals(listOf(result), harness.notifier.results)
        }

    @Test
    fun captchaIsHandedToTheUserAndNeverAnswered() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.page.pageKinds += PageKind.CAPTCHA

            val result = harness.run()

            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertEquals(
                NeedsUserReason.CAPTCHA,
                harness.notifier.userAlerts
                    .single()
                    .first,
            )
            assertEquals(1, harness.page.openItemAtUtc.size)
            assertEquals(0, harness.page.layerOpens)
            assertEquals(1, harness.store.eventsOf(TimelineKind.NEEDS_USER).size)
        }

    @Test
    fun rateWallBacksOffThenRecovers() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.pageKinds += PageKind.RATE_WALL
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 500)
            harness.winAt("30.00")

            val result = harness.run()

            val loads = harness.page.openItemAtUtc
            assertTrue(loads.size >= 2)
            // 1.5 s per load plus a backoff of 8 to 12 s.
            val gap = loads[1] - loads[0]
            assertTrue("gap $gap", gap in 9_500L..13_600L)
            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(SnipeOutcome.WON, result.outcome)
            assertTrue(harness.store.eventsOf(TimelineKind.PROBLEM).any { it.message.contains("Error Page") })
        }

    @Test
    fun unconfirmedReplyIsFollowedWithoutTouchingThePageAgain() =
        runTest {
            val harness = WindowHarness(this)
            harness.store.edit(1) { it.copy(leadSeconds = 8) }
            harness.page.replies += BidReply.Unconfirmed(null)
            harness.winAt("30.00")

            val result = harness.run()

            // The page allows one press: no second submit, no second typing, and the receipt keeps the real tap.
            assertEquals(1, harness.page.submitCalls)
            assertEquals(listOf(gbp("48.00")), harness.page.typed)
            assertEquals(1, harness.page.observerArms)
            assertEquals(1, harness.store.eventsOf(TimelineKind.BID_SENT).size)
            assertEquals(8_000L, result.bidLeadMillis)
            assertEquals(END_UTC - 8_000L, result.bidSentAtUtcMillis)
            // The reply was lost, but eBay's own page names the user as the winner.
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun lostReplyWithAShortLeadIsReadFromTheEndedPage() =
        runTest {
            val harness = WindowHarness(this)
            harness.store.edit(1) { it.copy(leadSeconds = 3) }
            harness.winAt("30.00")

            val result = harness.run()

            assertEquals(1, harness.page.submitsAtUtc.size)
            // The reply was lost, but eBay's own page names the user as the winner.
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun extendedBiddingWithTwoExtensionsThenWon() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 420)
            // Our bid lands in the last minute and resets the clock; a rival bids again a little later.
            harness.page.onSubmit = { harness.auction.endUtc += 60_000 }
            launch {
                delay(150_000)
                harness.auction.price = gbp("35.00")
                harness.auction.bidCount = 6
                harness.auction.endUtc = harness.auction.nowUtc() + 60_000
            }
            harness.winAt("36.00")

            val result = harness.run()

            assertEquals(SnipeOutcome.WON, result.outcome)
            assertEquals(SnipeStatus.WON, harness.snipe().status)
            assertEquals(2, harness.store.eventsOf(TimelineKind.EXTENDED).size)
            assertEquals(2, harness.notifier.phases.count { it == WindowPhase.EXTENDED })
            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(6_000L, result.bidLeadMillis)
            assertTrue(harness.snipe().extendedBidding)
            assertEquals(BASE_UTC + 210_000L, harness.snipe().endTimeUtcMillis)
        }

    @Test
    fun listingEndedEarlyPlacesNoBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.auction.endedEarly = true

            val result = harness.run()

            assertEquals(SnipeOutcome.ENDED_EARLY, result.outcome)
            assertEquals(SnipeStatus.ENDED_EARLY, harness.snipe().status)
            assertEquals(0, harness.page.layerOpens)
            assertEquals(listOf(result), harness.notifier.results)
        }

    @Test
    fun groupMemberAlreadyWonSkipsWithoutBidding() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.store.groups[7] = SnipeGroup(7, "Cameras")
            harness.store.edit(1) { it.copy(groupId = 7) }
            harness.store.put(testSnipe(id = 2, groupId = 7, status = SnipeStatus.WON, endUtc = END_UTC - 600_000))

            val result = harness.run()

            assertEquals(SnipeOutcome.SKIPPED_BY_GROUP, result.outcome)
            assertEquals(SnipeStatus.SKIPPED_BY_GROUP, harness.snipe().status)
            assertEquals(0, harness.pagesCreated)
            assertTrue(result.reason!!.contains("Item 2"))
        }

    @Test
    fun groupMemberStillUndecidedAtFireTimeSkipsThisOne() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.store.groups[7] = SnipeGroup(7, "Cameras")
            harness.store.edit(1) { it.copy(groupId = 7) }
            harness.store.put(testSnipe(id = 2, groupId = 7, status = SnipeStatus.ARMED, endUtc = END_UTC - 30_000))
            launch {
                // The other member's bid goes out first and is still being decided when this one would fire.
                delay(80_000)
                harness.store.edit(2) { it.copy(status = SnipeStatus.EXTENDED) }
            }

            val result = harness.run()

            assertEquals(SnipeOutcome.SKIPPED_BY_GROUP, result.outcome)
            assertTrue(harness.page.submitsAtUtc.isEmpty())
            assertEquals(1, harness.page.layerCloses)
        }

    @Test
    fun winCancelsTheRestOfTheGroup() =
        runTest {
            val harness = WindowHarness(this)
            harness.store.groups[7] = SnipeGroup(7, "Cameras")
            harness.store.edit(1) { it.copy(groupId = 7) }
            harness.store.put(testSnipe(id = 3, groupId = 7, status = SnipeStatus.ARMED, endUtc = END_UTC + 86_400_000))
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")

            harness.run()

            assertEquals(
                SnipeStatus.SKIPPED_BY_GROUP,
                harness.store.snipes
                    .getValue(3)
                    .status,
            )
            assertTrue(harness.notifier.results.any { it.outcome == SnipeOutcome.SKIPPED_BY_GROUP })
            assertEquals(
                SnipeOutcome.SKIPPED_BY_GROUP,
                harness.store.snipes
                    .getValue(3)
                    .result
                    ?.outcome,
            )
        }

    @Test
    fun submitIsDispatchedWithinFiftyMillisecondsOfTheTarget() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")

            harness.run()

            // The phone's clock runs 3 s fast: only true time puts the bid on target.
            val lateBy = harness.page.submitsAtUtc.single() - (END_UTC - 6_000L)
            assertTrue("late by $lateBy ms", lateBy in 0L..50L)
        }

    @Test
    fun secondWindowForTheSameSnipeWaitsInsteadOfBiddingAgain() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")

            val first = async { harness.run() }
            val second =
                async {
                    delay(45_000)
                    harness.run()
                }

            assertEquals(first.await(), second.await())
            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(1, harness.pagesCreated)
        }

    @Test
    fun restartAfterTheBidOnlyFollowsTheAuction() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.store.edit(1) { it.copy(status = SnipeStatus.LEADING) }
            harness.winAt("30.00")
            harness.auction.endUtc = BASE_UTC + 10_000

            val result = harness.run()

            assertEquals(SnipeOutcome.WON, result.outcome)
            assertEquals(0, harness.page.layerOpens)
        }

    @Test
    fun clockFallbackIsRecordedAndTheBidStillGoesOut() =
        runTest {
            val harness = WindowHarness(this)
            harness.time.sntpScript = ArrayDeque()
            harness.time.networkSkewMillis = 0
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")

            val result = harness.run()

            val clockEvent = harness.store.eventsOf(TimelineKind.CLOCK_SYNCED).single()
            assertTrue(clockEvent.message.contains("network time"))
            assertEquals(ClockSources.NETWORK, harness.clock.lastSync?.source)
            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun verificationInTheBidLayerIsHandedToTheUser() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.page.layerState =
                { LayerState.NeedsUser("Verify your phone number to bid", "layer: phone verification") }

            val result = harness.run()

            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertEquals(
                NeedsUserReason.VERIFICATION,
                harness.notifier.userAlerts
                    .single()
                    .first,
            )
            assertTrue(harness.page.typed.isEmpty())
            assertEquals(1, harness.page.layerCloses)
        }

    @Test
    fun fieldThatDoesNotHoldTheMaxStopsTheBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.page.fieldValueFor = { "480.00" }

            val result = harness.run()

            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertEquals(2, harness.page.typed.size)
            assertEquals(
                NeedsUserReason.BID_FAILED,
                harness.notifier.userAlerts
                    .single()
                    .first,
            )
        }

    @Test
    fun windowStartedFarTooEarlyHandsBackWithoutHoldingThePhone() =
        runTest {
            val harness = WindowHarness(this)
            harness.store.edit(1) { it.copy(endTimeUtcMillis = END_UTC + 3_600_000) }

            val result = harness.run()

            assertEquals(SnipeOutcome.NOT_PLACED, result.outcome)
            assertEquals(SnipeStatus.ARMED, harness.snipe().status)
            assertEquals(0, harness.pagesCreated)
            assertTrue(harness.time.trueUtc() - BASE_UTC < 10_000)
        }

    @Test
    fun browserCheckThatClearsInPlaceGoesOnToBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.pageKinds += PageKind.CHALLENGE
            // The check clears after a few seconds and the item page appears.
            var reads = 0
            harness.page.readListingResult = { if (++reads < 3) null else harness.auction.listing() }
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")

            val result = harness.run()

            // Waiting in place means no second load before the bid; the only other load reads the result.
            assertEquals(2, harness.page.openItemAtUtc.size)
            assertTrue(harness.page.openItemAtUtc[1] > END_UTC)
            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun watchOnlySnipeNeverOpensAPage() =
        runTest {
            val harness = WindowHarness(this)
            harness.store.edit(1) { it.copy(maxBid = null, status = SnipeStatus.WATCHING) }

            val result = harness.run()

            assertEquals(SnipeOutcome.NOT_PLACED, result.outcome)
            assertEquals(0, harness.pagesCreated)
        }

    @Test
    fun endThatMovesFarAwayHandsTheSnipeBackToTheScheduler() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.auction.endUtc = END_UTC + 3_600_000

            val result = harness.run()

            assertEquals(SnipeOutcome.NOT_PLACED, result.outcome)
            assertEquals(SnipeStatus.ARMED, harness.snipe().status)
            assertEquals(END_UTC + 3_600_000, harness.snipe().endTimeUtcMillis)
            assertFalse(harness.snipe().status.isFinal)
            assertNotNull(result.reason)
        }

    @Test
    fun cancelWhileThePageLoadsIsNotLost() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            // The item page loads from 30.0 s to 31.5 s; the user cancels in the middle of it.
            launch {
                delay(30_700)
                harness.store.edit(1) { it.copy(status = SnipeStatus.CANCELLED) }
            }

            val result = harness.run()

            assertEquals(SnipeOutcome.CANCELLED, result.outcome)
            assertEquals(SnipeStatus.CANCELLED, harness.snipe().status)
            assertEquals(0, harness.page.layerOpens)
        }

    @Test
    fun aMaxBelowEbaysMinimumEndsAsPricePassedTheMaxWithoutPressing() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.page.typedMinimum = { gbp("60.00") }
            harness.page.typedInlineError = { "Enter £60.00 or more" }
            harness.page.typedProblem = { TypingProblem.BELOW_MINIMUM }

            val result = harness.run()

            assertEquals(SnipeOutcome.PRICE_PASSED_MAX, result.outcome)
            assertEquals(
                gbp("60.00"),
                harness.notifier.priceAlerts
                    .last()
                    .second,
            )
            assertTrue(harness.notifier.userAlerts.isEmpty())
        }

    @Test
    fun aBidButtonThatNeverEnablesFailsEarlyWithTheReason() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            harness.page.typedInlineError = { "Kestrel: eBay's bid button stayed disabled after typing the amount" }
            harness.page.typedProblem = { TypingProblem.BUTTON_DISABLED }

            val result = harness.run()

            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertTrue(result.reason!!, result.reason!!.contains("stayed disabled"))
            // Looked again without typing before typing anew, and gave up long before the fire time.
            assertEquals(2, harness.page.typed.size)
            assertTrue(harness.page.checks >= 4)
            assertTrue(harness.time.trueUtc() < END_UTC - 20_000)
        }

    @Test
    fun aPressThePageProvablyNeverMadeIsNotRecordedAsABid() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.submitError = NotPressedException("The typed maximum was not verified on this page")

            val result = harness.run()

            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertEquals(SnipeStatus.FAILED, harness.snipe().status)
            assertEquals(null, result.bidSentAtUtcMillis)
            assertEquals(null, result.bidLeadMillis)
            assertTrue(harness.store.eventsOf(TimelineKind.BID_SENT).isEmpty())
            assertFalse(WindowPhase.BID_SENT in harness.notifier.phases)
            assertTrue(result.reason!!, result.reason!!.contains("the typed maximum was not verified on this page"))
            assertEquals(
                NeedsUserReason.BID_FAILED,
                harness.notifier.userAlerts
                    .single()
                    .first,
            )
            // The layer was not used for a bid, so it is closed like a person would close it.
            assertEquals(1, harness.page.layerCloses)
        }

    @Test
    fun aCheckThatStopsThePressIsHandedToTheUser() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.submitError =
                NotPressedException("eBay is asking for something before the bid (verification or terms)", true)

            val result = harness.run()

            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertEquals(null, result.bidSentAtUtcMillis)
            assertEquals(
                NeedsUserReason.VERIFICATION,
                harness.notifier.userAlerts
                    .single()
                    .first,
            )
        }

    /** The pre-tap BID_SENT write takes longer than its 150 ms limit once, so it never lands. */
    private fun WindowHarness.slowFirstBidSentWrite() {
        var slow = true
        store.updateDelayMillis = { written ->
            if (slow && written.status == SnipeStatus.BID_SENT) {
                slow = false
                200L
            } else {
                0L
            }
        }
    }

    @Test
    fun aBidWhosePreTapWriteMissedIsStoredAsSentAfterTheTap() =
        runTest {
            val harness = WindowHarness(this)
            harness.slowFirstBidSentWrite()
            harness.page.replies += BidReply.Unconfirmed(null)
            // The ended page says nothing about the user's standing, so the result stays open.
            harness.auction.finalPage = FinalPage(price = gbp("30.00"), bidCount = 5)
            harness.auction.signedIn = null

            val result = harness.run()

            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(SnipeOutcome.UNKNOWN, result.outcome)
            // Stored as BIDDING the platform would read "no bid": a false alert, a group sibling let through.
            assertTrue("${harness.snipe().status}", harness.snipe().status in BID_SENT_STATUSES)
        }

    @Test
    fun aWindowStoppedAfterATapWhoseWriteMissedStillRecordsTheBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.slowFirstBidSentWrite()
            // eBay's reply never comes, and the window is stopped while it waits for it.
            harness.page.replies += BidReply.Unconfirmed(null)
            val window = launch { harness.run() }
            delay(115_000)
            window.cancel()
            window.join()

            assertEquals(1, harness.page.submitsAtUtc.size)
            assertTrue("${harness.snipe().status}", harness.snipe().status in BID_SENT_STATUSES)
            assertEquals(SnipeOutcome.UNKNOWN, harness.snipe().result?.outcome)
        }

    @Test
    fun theBiddingLiveUpdateIsPostedBeforeThePreciseWaitNotBetweenItAndTheTap() =
        runTest {
            val harness = WindowHarness(this)
            harness.notifier.clock = { harness.time.trueUtc() }
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")

            harness.run()

            val biddingShownAt =
                harness.notifier.phaseTimes
                    .first { it.first == WindowPhase.BIDDING }
                    .second
            assertTrue("shown at ${biddingShownAt - END_UTC}", biddingShownAt < END_UTC - 6_000L)
        }

    @Test
    fun aTapThatTheStoreWriteMadeTooLateIsNotSent() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            // Storage stalls: the pre-tap BID_SENT write blocks the main thread for 5.5 s, past the latest dispatch.
            var stalled = false
            harness.store.updateDelayMillis = { written ->
                if (!stalled && written.status == SnipeStatus.BID_SENT) {
                    stalled = true
                    harness.time.elapsedShiftMillis = 5_500
                }
                0L
            }

            val result = harness.run()

            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertTrue(result.reason!!, result.reason!!.contains("too late"))
            assertEquals(null, result.bidSentAtUtcMillis)
            assertTrue(harness.store.eventsOf(TimelineKind.BID_SENT).isEmpty())
        }

    @Test
    fun bidSentIsStoredBeforeTheTap() =
        runTest {
            val harness = WindowHarness(this)
            var statusAtTap: SnipeStatus? = null
            harness.page.onSubmit = { statusAtTap = harness.snipe().status }
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")

            harness.run()

            assertEquals(SnipeStatus.BID_SENT, statusAtTap)
        }

    @Test
    fun lostReplyKeepsTheResultOpenWhenTheEndedPageSaysNothing() =
        runTest {
            val harness = WindowHarness(this)
            harness.store.edit(1) { it.copy(leadSeconds = 8) }
            harness.page.replies += BidReply.Unconfirmed(null)
            // The ended page says nothing about the user's standing.
            harness.auction.finalPage = FinalPage(price = gbp("30.00"), bidCount = 5)
            harness.auction.signedIn = null

            val result = harness.run()

            // The tap may have counted, so this is neither a failure nor a loss, and the group stays blocked.
            assertEquals(1, harness.page.submitCalls)
            assertEquals(SnipeOutcome.UNKNOWN, result.outcome)
            assertTrue(harness.snipe().status in BID_SENT_STATUSES)
        }

    @Test
    fun lostReplyWithThePriceAboveTheMaxIsLost() =
        runTest {
            val harness = WindowHarness(this)
            harness.store.edit(1) { it.copy(leadSeconds = 8) }
            harness.page.onSubmit = {
                harness.auction.price = gbp("50.00")
                harness.auction.bidCount = 7
            }
            harness.auction.finalPage = FinalPage(price = gbp("50.00"), bidCount = 7)

            val result = harness.run()

            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(SnipeOutcome.LOST, result.outcome)
        }

    @Test
    fun endThatMovesEarlierIsStoredAndTheBidFollowsIt() =
        runTest {
            val harness = WindowHarness(this)
            launch {
                delay(60_000)
                harness.auction.endUtc = END_UTC - 10_000
            }
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")

            harness.run()

            assertEquals(END_UTC - 10_000, harness.snipe().endTimeUtcMillis)
            val lateBy = harness.page.submitsAtUtc.single() - (END_UTC - 16_000L)
            assertTrue("late by $lateBy ms", lateBy in 0L..50L)
        }

    @Test
    fun leadEditedBeforeTheLockMovesTheBid() =
        runTest {
            val harness = WindowHarness(this)
            launch {
                delay(60_000)
                harness.store.edit(1) { it.copy(leadSeconds = 10) }
            }
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")

            val result = harness.run()

            val lateBy = harness.page.submitsAtUtc.single() - (END_UTC - 10_000L)
            assertTrue("late by $lateBy ms", lateBy in 0L..50L)
            assertEquals(10_000L, result.bidLeadMillis)
        }

    @Test
    fun auctionThatKeepsExtendingEndsTheWindowWithAnOpenResult() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.page.onSubmit = { harness.auction.endUtc += 86_400_000L }

            val result = harness.run()

            assertEquals(SnipeOutcome.UNKNOWN, result.outcome)
            assertTrue(harness.snipe().status in BID_SENT_STATUSES)
            assertTrue(harness.time.trueUtc() < END_UTC + 30 * 60_000L)
        }

    @Test
    fun stoppedBeforeTheBidHandsTheSnipeBackAndTellsTheUser() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            val window = launch { harness.run() }
            delay(80_000)
            window.cancel()
            window.join()

            assertEquals(SnipeStatus.ARMED, harness.snipe().status)
            assertEquals(
                NeedsUserReason.BID_FAILED,
                harness.notifier.userAlerts
                    .single()
                    .first,
            )
            assertEquals(1, harness.page.layerCloses)
        }

    @Test
    fun stoppedAfterTheBidLeavesAnOpenResultForTheNextCheck() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            val window = launch { harness.run() }
            delay(116_000)
            window.cancel()
            window.join()

            assertEquals(SnipeStatus.LEADING, harness.snipe().status)
            assertEquals(SnipeOutcome.UNKNOWN, harness.snipe().result?.outcome)
            assertTrue(harness.notifier.userAlerts.isEmpty())
        }

    @Test
    fun unreadableInstructionAtTheLockPlacesNoBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            launch {
                delay(100_000)
                harness.store.readsFail = true
            }

            val result = harness.run()

            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertTrue(harness.page.submitsAtUtc.isEmpty())
        }

    @Test
    fun oneSecondLeadIsNotRefusedAsTooLate() =
        runTest {
            val harness = WindowHarness(this)
            harness.store.edit(1) { it.copy(leadSeconds = 1) }
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 300)
            harness.winAt("30.00")
            // The phone wakes up 90 ms late for the fire time, as a busy main thread can.
            launch {
                delay(118_995)
                harness.time.elapsedShiftMillis = 95
            }

            val result = harness.run()

            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun strayEndedRepliesThatThePageDoesNotConfirmStillBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")
            // With the layer open, two live updates in a row wrongly say the auction ended.
            launch {
                delay(85_000)
                harness.auction.strayEndedReplies = 2
            }

            val result = harness.run()

            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(2, harness.page.layerOpens)
            assertEquals(listOf(gbp("48.00"), gbp("48.00")), harness.page.typed)
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun strayEndedRepliesWithAShortLeadReopenTheLayerAtOnceAndBidOnTime() =
        runTest {
            val harness = WindowHarness(this)
            harness.store.edit(1) { it.copy(leadSeconds = 3) }
            // A slow phone: the layer takes 3 s to open and typing 1.3 s.
            harness.page.layerOpenMillis = 3_000
            harness.page.typingMillis = 1_300
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")
            launch {
                delay(90_000)
                harness.auction.strayEndedReplies = 2
            }

            val result = harness.run()

            assertEquals(1, harness.page.submitsAtUtc.size)
            val lateBy = harness.page.submitsAtUtc.single() - (END_UTC - 3_000L)
            assertTrue("late by $lateBy ms", lateBy in 0L..50L)
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun strayEndedRepliesWhoseConfirmingReloadFailsStillBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")
            launch {
                delay(85_000)
                harness.auction.strayEndedReplies = 2
                // The reload that should confirm it meets eBay's slow-down page.
                harness.page.pageKinds += PageKind.RATE_WALL
            }

            val result = harness.run()

            assertEquals(1, harness.page.submitsAtUtc.size)
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun aRendererThatDiesWhileTheItemLoadsIsReplacedAndTheBidStillGoesOut() =
        runTest {
            val harness = WindowHarness(this)
            val fresh = ScriptedBidPage(testScheduler, harness.auction)
            fresh.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.laterPages += fresh
            harness.page.dieOnNextOpenItem = true
            harness.winAt("30.00")

            val result = harness.run()

            assertEquals(2, harness.pagesCreated)
            assertTrue(harness.page.closed)
            assertEquals(1, fresh.submitsAtUtc.size)
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun aRendererThatDiesAfterTheItemLoadedIsReplacedBeforeTheLayerOpens() =
        runTest {
            val harness = WindowHarness(this)
            val fresh = ScriptedBidPage(testScheduler, harness.auction)
            fresh.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.laterPages += fresh
            harness.winAt("30.00")
            launch {
                // The page loaded at T-90 s; Android ends its renderer at T-50 s.
                delay(70_000)
                harness.page.dead = true
            }

            val result = harness.run()

            assertEquals(2, harness.pagesCreated)
            assertEquals(1, fresh.openItemAtUtc.count { it < END_UTC })
            assertEquals(listOf(gbp("48.00")), fresh.typed)
            assertEquals(1, fresh.submitsAtUtc.size)
            assertEquals(SnipeOutcome.WON, result.outcome)
        }

    @Test
    fun aRendererThatDiesTooCloseToTheBidIsNotReplacedAndNothingIsSent() =
        runTest {
            val harness = WindowHarness(this)
            harness.laterPages +=
                ScriptedBidPage(testScheduler, harness.auction).also { it.submitError = AssertionError() }
            launch {
                // Six seconds before the bid: too little time to load a new page, open the layer and type.
                delay(108_000)
                harness.page.dead = true
            }

            val result = harness.run()

            assertEquals(SnipeOutcome.FAILED, result.outcome)
            assertEquals(null, result.bidSentAtUtcMillis)
            assertTrue(harness.store.eventsOf(TimelineKind.BID_SENT).isEmpty())
        }

    @Test
    fun restartDoesNotTrustAMaxEditedAfterTheBid() =
        runTest {
            val harness = WindowHarness(this)
            harness.forbidSubmit()
            // The bid of £48 went out in an earlier run; the stored max was raised to £100 afterwards.
            harness.store.edit(1) { it.copy(status = SnipeStatus.LEADING, maxBid = gbp("100.00")) }
            harness.auction.endUtc = BASE_UTC + 10_000
            harness.auction.finalPage = FinalPage(price = gbp("60.00"), bidCount = 9)
            harness.auction.signedIn = null

            val result = harness.run()

            // £60 is below the stored max but maybe above the real bid: not provably a win.
            assertEquals(SnipeOutcome.UNKNOWN, result.outcome)
        }

    @Test
    fun editAfterTheLockIsIgnoredForTheBidButKept() =
        runTest {
            val harness = WindowHarness(this)
            harness.page.replies += BidReply.Leading("Your current bid puts you in the lead.", 400)
            harness.winAt("30.00")
            launch {
                delay(113_000)
                harness.store.edit(1) { it.copy(leadSeconds = 9) }
            }

            val result = harness.run()

            assertEquals(6_000L, result.bidLeadMillis)
            assertEquals(9, harness.snipe().leadSeconds)
        }
}
