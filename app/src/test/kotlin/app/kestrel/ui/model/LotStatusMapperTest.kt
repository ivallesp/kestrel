package app.kestrel.ui.model

import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.model.TimelineKind
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.CatalogueFixture.at
import app.kestrel.ui.fixture.CatalogueFixture.gbp
import app.kestrel.ui.fixture.CatalogueFixture.millis
import app.kestrel.ui.fixture.LotMoment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LotStatusMapperTest {
    private val fixture = CatalogueFixture

    @Test
    fun watchingWithoutAMax() {
        assertEquals(LotStatus.Watching, fixture.polaroidWatching.status)
    }

    @Test
    fun armedCarriesTheMaxTheBidTimeAndTheLead() {
        assertEquals(LotStatus.Armed(gbp("120.00"), at("2025-11-09T18:21:34Z"), 6), fixture.canonArmed.status)
        assertEquals(LotStatus.Armed(gbp("45.00"), at("2025-11-10T20:09:59Z"), 6), fixture.miniDiscArmed.status)
    }

    @Test
    fun overMaxCarriesThePriceTheMaxAndEbaysNextBid() {
        assertEquals(LotStatus.OverMax(gbp("2150.00"), gbp("1900.00"), gbp("2200.00")), fixture.omegaOverMax.status)
    }

    @Test
    fun anOpenWindowTurnsOverMaxWhenThePricePassesTheMax() {
        assertEquals(
            LotStatus.OverMax(gbp("121.00"), gbp("120.00"), gbp("123.00")),
            fixture.canonOverMaxInTheWindow.status,
        )
    }

    @Test
    fun signInNeeded() {
        assertEquals(LotStatus.SignInNeeded(at("2025-11-09T18:21:34Z")), fixture.canonSignInNeeded.status)
    }

    @Test
    fun captchaFromACheckOrFromTheDryRun() {
        assertEquals(LotStatus.Captcha(at("2025-11-09T18:21:34Z")), fixture.canonCaptcha.status)
        val dryRunCaptcha = dryRunFailedWith("CAPTCHA")
        assertEquals(LotStatus.Captcha(at("2025-11-09T18:21:34Z")), dryRunCaptcha.status)
    }

    @Test
    fun phoneCheckFromTheDryRunOrFromACheck() {
        assertEquals(LotStatus.PhoneCheck(at("2025-11-09T18:21:34Z")), fixture.canonPhoneCheck.status)
        val handOff = handedOff("VERIFICATION")
        assertEquals(LotStatus.PhoneCheck(at("2025-11-09T18:21:34Z")), handOff.status)
    }

    @Test
    fun dryRunFailedSaysWhatTheDryRunMet() {
        assertEquals(
            LotStatus.DryRunFailed(
                at("2025-11-09T18:11:40.200Z"),
                DryRunCause.SIGN_IN_PAGE,
                at("2025-11-09T18:21:34Z"),
            ),
            fixture.canonDryRunFailed.status,
        )
        assertEquals(DryRunCause.OTHER, (dryRunFailedWith("DRY_RUN_FAILED").status as LotStatus.DryRunFailed).cause)
    }

    @Test
    fun offlineOnlyMattersWhenTheBidIsWithinTheHour() {
        assertEquals(LotStatus.OfflineNearBid(at("2025-11-09T18:21:34Z")), fixture.canonOffline.status)
        val offline = fixture.canonOffline.connectivity
        assertEquals(LotStatus.Armed::class, fixture.gameBoyArmed.copy(connectivity = offline).status::class)
        assertEquals(
            LotStatus.OfflineNearBid::class,
            fixture.canonFinalMinute.copy(connectivity = offline).status::class,
        )
    }

    @Test
    fun threeFailedChecksInTheFinalHourAreUnreachable() {
        assertEquals(LotStatus.Unreachable(at("2025-11-09T18:21:34Z")), fixture.canonUnreachable.status)
    }

    @Test
    fun aCheckThatWorkedSinceTheFailuresClearsUnreachable() {
        val moment = fixture.canonUnreachable
        val worked = moment.snipe.copy(lastCheckedAtUtcMillis = millis("2025-11-09T18:08:00Z"))
        assertEquals(LotStatus.Armed::class, moment.copy(snipe = worked).status::class)
        assertNull(LotStatusMapper.failedCheckAt(worked, moment.events))
        assertEquals(at("2025-11-09T18:05:00.200Z"), LotStatusMapper.failedCheckAt(moment.snipe, moment.events))
    }

    @Test
    fun anUnrecognisedPageIsNeedsYouWithTheEnginesWords() {
        val status = fixture.omegaNeedsYou.status as LotStatus.NeedsYou
        assertEquals("eBay showed \"Confirm your age\" instead of the item. Open it to check.", status.message)
    }

    @Test
    fun aWindowThatClosedWithoutBiddingIsNeedsYouEvenAfterAnOldCaptcha() {
        val captcha = fixture.canonCaptcha
        val windowStopped =
            captcha.copy(
                events =
                    captcha.events +
                        event(TimelineKind.WINDOW_OPENED, "2025-11-09T18:19:40Z", "Bid window opened.") +
                        event(
                            TimelineKind.PROBLEM,
                            "2025-11-09T18:21:38Z",
                            "The bid window closed without placing a bid. Open the listing to check it.",
                        ),
                now = at("2025-11-09T18:21:39Z"),
            )
        assertEquals(
            LotStatus.NeedsYou(
                "The bid window closed without placing a bid. Open the listing to check it.",
                at("2025-11-09T18:21:34Z"),
            ),
            windowStopped.status,
        )
    }

    @Test
    fun theWindowIsArmedUntilTheFinalMinute() {
        val early = fixture.canonFinalMinute.copy(now = at("2025-11-09T18:20:20Z"))
        assertEquals(LotStatus.Armed(gbp("120.00"), at("2025-11-09T18:21:34Z"), 6), early.status)
    }

    @Test
    fun finalMinuteLocksEditsThreeSecondsBeforeTheBid() {
        assertEquals(
            LotStatus.FinalMinute(gbp("120.00"), at("2025-11-09T18:21:34Z"), 6, at("2025-11-09T18:21:31Z")),
            fixture.canonFinalMinute.status,
        )
    }

    @Test
    fun bidSentThenNoReplyAfterFiveSeconds() {
        assertEquals(LotStatus.BidSent(gbp("120.00"), at("2025-11-09T18:21:33.900Z")), fixture.canonBidSent.status)
        assertEquals(LotStatus.NoReply(at("2025-11-09T18:21:33.900Z")), fixture.canonNoReply.status)
    }

    @Test
    fun aRefusedOnlyBidIsBidFailedBeforeTheEnd() {
        val refused =
            fixture.canonBidFailed.copy(
                snipe = fixture.canonBidFailed.snipe.copy(status = SnipeStatus.BID_SENT, result = null),
                now = at("2025-11-09T18:21:36Z"),
            )
        assertEquals(
            LotStatus.BidFailed("Your bid wasn’t placed. Please try again.", at("2025-11-09T18:21:34.700Z")),
            refused.status,
        )
    }

    @Test
    fun inTheLeadAndOutbidQuoteEbay() {
        assertEquals(
            LotStatus.InTheLead("Your current bid puts you in the lead.", at("2025-11-09T18:21:40Z"), 800),
            fixture.canonInTheLead.status,
        )
        assertEquals(
            LotStatus.Outbid(
                "You’ve been outbid. Another bidder’s maximum is higher.",
                at("2025-11-09T18:21:40Z"),
                800,
            ),
            fixture.canonOutbid.status,
        )
    }

    @Test
    fun outbidByThePriceAloneHasNoQuote() {
        val priceOutbid =
            fixture.canonInTheLead.copy(snipe = fixture.canonInTheLead.snipe.copy(status = SnipeStatus.OUTBID))
        assertNull((priceOutbid.status as LotStatus.Outbid).ebayText)
    }

    @Test
    fun extendedShowsTheNewEnd() {
        assertEquals(LotStatus.Extended(at("2025-11-11T19:56:42Z")), fixture.walkmanExtended.status)
    }

    @Test
    fun anyStatusBeforeAResultReadsTheResultOnceTheEndPasses() {
        assertEquals(LotStatus.ReadingResult(at("2025-11-09T18:21:40Z")), fixture.canonReadingResult.status)
        val afterEnd = at("2025-11-09T18:22:00Z")
        listOf(fixture.canonArmed, fixture.canonCaptcha, fixture.canonBidSent, fixture.canonFinalMinute).forEach {
            assertEquals(LotStatus.ReadingResult(at("2025-11-09T18:21:40Z")), it.copy(now = afterEnd).status)
        }
    }

    @Test
    fun wonAndLostCarryThePrices() {
        assertEquals(LotStatus.Won(gbp("101.00"), gbp("120.00"), at("2025-11-09T18:21:40Z")), fixture.canonWon.status)
        assertEquals(
            LotStatus.Won(gbp("34.00"), gbp("45.00"), at("2025-11-08T20:14:09Z")),
            fixture.gameAndWatchWon.status,
        )
        assertEquals(
            LotStatus.Lost(gbp("79.00"), gbp("65.00"), at("2025-11-07T19:41:00Z"), bidSent = true),
            fixture.nintendo64Lost.status,
        )
    }

    @Test
    fun aLossWithoutABidSaysSo() {
        val passedTheMax =
            fixture.canonArmed.copy(
                snipe =
                    fixture.canon.copy(
                        status = SnipeStatus.LOST,
                        lastPrice = gbp("130.00"),
                        result = SnipeResult(SnipeOutcome.PRICE_PASSED_MAX, gbp("130.00"), null, null, null, null, "x"),
                    ),
            )
        assertEquals(false, (passedTheMax.status as LotStatus.Lost).bidSent)
    }

    @Test
    fun endedEarlyAndCancels() {
        assertEquals(LotStatus.EndedEarly(at("2025-11-12T09:30:00Z")), fixture.gameBoyEndedEarly.status)
        assertEquals(LotStatus.CancelledByYou(at("2025-11-08T16:20:00Z")), fixture.gameBoyCancelled.status)
        assertEquals(
            LotStatus.CancelledByGroup("Portable audio", "Sony MiniDisc Walkman MZ-N707", auctionRunning = true),
            fixture.walkmanCancelledByGroup.status,
        )
    }

    @Test
    fun aGroupSkipWithoutAWinIsNotCalledAWin() {
        val skipped = fixture.walkmanCancelledByGroup.copy(group = GroupContext("Portable audio", wonTitle = null))
        assertEquals(LotStatus.CancelledByGroup("Portable audio", null, auctionRunning = true), skipped.status)
    }

    @Test
    fun bidNotSentWhenTheBidNeverLeftThePhone() {
        val status = fixture.canonBidNotSent.status as LotStatus.BidNotSent
        assertEquals(at("2025-11-09T18:21:20.300Z"), status.signedOutAt)
        assertEquals(at("2025-11-09T18:21:20.400Z"), status.at)
    }

    @Test
    fun bidFailedWhenEbayRefusedIt() {
        val status = fixture.canonBidFailed.status
        assertTrue(status is LotStatus.BidFailed)
        assertEquals("Your bid wasn’t placed. Please try again.", (status as LotStatus.BidFailed).ebayText)
    }

    @Test
    fun theFixtureCoversEveryCase() {
        val moments =
            listOf(
                fixture.polaroidWatching,
                fixture.canonArmed,
                fixture.omegaOverMax,
                fixture.canonSignInNeeded,
                fixture.canonCaptcha,
                fixture.canonPhoneCheck,
                fixture.canonDryRunFailed,
                fixture.canonOffline,
                fixture.canonUnreachable,
                fixture.omegaNeedsYou,
                fixture.canonFinalMinute,
                fixture.canonBidSent,
                fixture.canonInTheLead,
                fixture.canonOutbid,
                fixture.canonNoReply,
                fixture.walkmanExtended,
                fixture.canonReadingResult,
                fixture.canonWon,
                fixture.nintendo64Lost,
                fixture.gameBoyEndedEarly,
                fixture.gameBoyCancelled,
                fixture.walkmanCancelledByGroup,
                fixture.canonBidNotSent,
                fixture.canonBidFailed,
            )
        assertEquals(LotStatusKind.entries.toSet(), moments.map { it.status.kind }.toSet())
    }

    @Test
    fun editsLockAtTheEarlierOfTheUiAndEngineDeadlines() {
        assertEquals(at("2025-11-09T18:21:31Z"), LotTimes.editsLockAt(fixture.canon))
        assertEquals(at("2025-11-09T18:21:34Z"), LotTimes.bidAt(fixture.canon))
    }

    @Test
    fun quotedTextIsEbaysWordsInsideTheMessage() {
        assertEquals(
            "Your current bid puts you in the lead.",
            LotStatusMapper.quotedText("eBay: \"Your current bid puts you in the lead.\""),
        )
        assertNull(LotStatusMapper.quotedText("eBay's reply could not be read within 4 s."))
    }

    private fun dryRunFailedWith(reason: String): LotMoment {
        val moment = fixture.canonDryRunFailed
        val events =
            moment.events.dropLast(1) +
                event(TimelineKind.DRY_RUN, "2025-11-09T18:11:40.200Z", "Dry run failed.", reason)
        return moment.copy(events = events)
    }

    private fun handedOff(reason: String): LotMoment {
        val moment = fixture.canonCaptcha
        val events =
            moment.events.dropLast(1) +
                event(TimelineKind.NEEDS_USER, "2025-11-09T18:12:01Z", "eBay needs you.", reason)
        return moment.copy(events = events)
    }

    private fun event(
        kind: TimelineKind,
        time: String,
        message: String,
        reason: String? = null,
    ): TimelineEvent =
        TimelineEvent(
            id = millis(time),
            snipeId = fixture.canon.id,
            atUtcMillis = millis(time),
            kind = kind,
            message = message,
            detail = reason?.let { "{\"trueUtcMillis\":${millis(time)},\"reason\":\"$it\"}" },
        )
}
