package app.kestrel.ui.screens.lot

import app.kestrel.ui.components.TimelineMoment
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.chipText
import app.kestrel.ui.screens.lot.LotFixtures.lotState
import app.kestrel.ui.screens.lot.LotFixtures.page
import app.kestrel.ui.theme.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * S3's words and rules (DESIGN S3, 3.12, 3.13), built from the design fixture through the real status mapper. Units
 * are joined to their numbers by U+202F, as the formatters write them.
 */
class LotStateBuilderTest {
    private val fixture = CatalogueFixture

    @Test
    fun theFinalMinuteReadsAsInTheDesign() {
        val state = fixture.canonFinalMinute.lotState()
        assertEquals("Final minute", state.chipText)
        assertEquals(StatusTone.Bidding, state.status.tone)
        assertEquals(
            CountdownText("36", "s left", finalMinute = true, "36 seconds left. Kestrel bids in 30 seconds."),
            state.countdown,
        )
        assertEquals("Today · GMT", state.times.heading)
        assertEquals(
            listOf(
                TimeRowText("Bids", "18:21:34"),
                TimeRowText("Ends", "18:21:40"),
                TimeRowText("Now", "18:21:04", ticks = true),
            ),
            state.times.rows,
        )
        val card = state.card!!
        assertEquals("Kestrel bids £120.00 in 30\u202Fs", card.title.text)
        assertEquals("At 18:21:34, 6\u202Fs before the end", card.detail)
        assertEquals(CardGlyph.Gavel, card.glyph)
        assertEquals(
            EditRowState(LotAction.EDIT_MAX, "Until 18:21:31, 3\u202Fs before the bid", enabled = true),
            state.edit,
        )
        assertEquals("Up £8.00 from £88.00", state.money.startCaption?.text)
        assertEquals("£24.00 below your\u00A0max", state.money.endCaption?.text)
        assertEquals("At most £126.50 with postage", state.money.note)
    }

    @Test
    fun theScaleStepsOnceASecondAndMarksTheBid() {
        val scale = fixture.canonFinalMinute.lotState().scale!!
        assertEquals(24, scale.elapsedSeconds)
        assertEquals(0.9f, scale.bidFraction!!, 0.001f)
        assertEquals("Final minute", scale.startLabel)
        assertFalse(scale.flat)
        assertEquals("Final minute: 36 seconds left. Kestrel bids in 30 seconds.", scale.spoken)
    }

    @Test
    fun theChecksSayOnlyWhatPassedForThisLot() {
        assertEquals(
            listOf(
                "Signed in as ${CatalogueFixture.ACCOUNT} · checked 18:20:55",
                "Dry run at 18:11:40 · nothing was sent",
                "Clock 0.04\u202Fs off Google time",
            ),
            fixture.canonFinalMinute.lotState().checks,
        )
    }

    @Test
    fun noScreenStatesAStepBeforeItHappens() {
        // At 18:08:33 the fixture already holds the 18:11:40 dry run; the screen must not show it (rule MF31).
        val state = LotFixtures.canonArmedToday.lotState()
        assertTrue(state.checks.none { it.startsWith("Dry run") })
        assertEquals("Dry run about 18:11, 10\u202Fmin before the end.", state.note)
        assertTrue(state.timeline.none { it.moment == TimelineMoment.Past && it.title.startsWith("Dry run") })
        assertTrue(state.timeline.any { it.moment == TimelineMoment.Future && it.title == "Dry run" })
    }

    @Test
    fun afterTheBidTheColumnSaysSentAndNothingClaimsMore() {
        val sent = fixture.canonBidSent.lotState()
        assertEquals(TimeRowText("Sent", "18:21:33.9"), sent.times.rows.first())
        assertEquals("Bid sent at 18:21:33.9", sent.card!!.title.text)
        assertEquals("Waiting for eBay’s reply", sent.card!!.detail)
        assertEquals(CardGlyph.Waiting, sent.card!!.glyph)
        assertTrue(sent.scale!!.flat)
        assertNull(sent.edit)
        assertEquals(MenuEntry("Bid already sent", enabled = false), sent.menu.stopSniping)
        // 5.6 s before the end: a partial second counts as a whole one, so it never reads "0 s" early.
        assertEquals("6 seconds left.", sent.countdown.spoken)

        // Before the bid no label says "sent" (rule MF20).
        assertTrue(
            fixture.canonFinalMinute
                .lotState()
                .times.rows
                .none { it.label == "Sent" },
        )
    }

    @Test
    fun eBaysRepliesAreQuotedExactly() {
        val lead = fixture.canonInTheLead.lotState()
        assertEquals("In the lead · eBay replied in 0.8\u202Fs", lead.card!!.title.text)
        assertEquals("“Your current bid puts you in the lead.” Not won until it ends.", lead.card!!.detail)
        assertEquals("Up £2.00 from £96.00", lead.money.startCaption?.text)

        val outbid = fixture.canonOutbid.lotState()
        assertEquals("Outbid · eBay replied in 0.8\u202Fs", outbid.card!!.title.text)
        // eBay's sentence says why; Kestrel adds nothing that repeats it.
        assertEquals("“You’ve been outbid. Another bidder’s maximum is higher.”", outbid.card!!.detail)
        assertEquals(
            "No reply from eBay yet",
            fixture.canonNoReply
                .lotState()
                .card!!
                .title.text,
        )
    }

    @Test
    fun anExtensionReBasesTheScaleAndSaysWhoResetTheClock() {
        val state = fixture.walkmanExtended.lotState()
        // The chip says "Extended"; the card says when it now ends.
        assertEquals("Extended", state.chipText)
        assertEquals("Extended · now ends 19:56:42", state.card!!.title.text)
        assertEquals("44", state.countdown.figure)
        assertEquals("Extended", state.scale!!.startLabel)
        assertNull("Kestrel's bid sits at the start of the re-based scale", state.scale!!.bidFraction)
        assertEquals(
            "Kestrel’s bid at 19:55:42 reset the clock to 60\u202Fs. Kestrel is still following. " +
                "You’re in the lead so far.",
            state.card!!.detail,
        )
        assertEquals(LotStateBuilder.EXTENDED_BIDDING, state.extendedNote)
        assertEquals("Signed in as ${CatalogueFixture.ACCOUNT} · checked 19:55:42", state.checks.single())
    }

    @Test
    fun farFromTheEndThereIsNoScaleNoCardAndNoSeconds() {
        val state = fixture.gameBoyArmed.lotState()
        assertEquals("7\u202Fd 3\u202Fh", state.countdown.figure)
        assertEquals("left", state.countdown.unit)
        assertEquals("7 days 3 hours left. Kestrel bids on Sun 16 Nov at 21:43.", state.countdown.spoken)
        assertEquals("Sun 16 Nov · GMT", state.times.heading)
        // Without seconds BIDS and ENDS would read the same: the bid goes under the end in words.
        assertEquals(listOf(TimeRowText("Ends", "21:43")), state.times.rows)
        assertEquals("Bids 6\u202Fs before", state.times.note)
        assertNull(state.scale)
        assertNull(state.card)
        assertEquals("Dry run about 21:33, 10\u202Fmin before the end.", state.note)
    }

    @Test
    fun underAnHourTheTimesHaveSecondsAndNow() {
        val state = LotFixtures.canonArmedToday.lotState()
        assertEquals("13\u202Fm 07\u202Fs", state.countdown.figure)
        assertEquals(listOf("Bids", "Ends", "Now"), state.times.rows.map { it.label })
        assertEquals(
            "18:21:34",
            state.times.rows
                .first()
                .time,
        )
    }

    @Test
    fun theEditsLockThreeSecondsBeforeTheBid() {
        val locked = LotFixtures.canonLocked.lotState()
        assertEquals(
            EditRowState(LotAction.EDIT_MAX, "Locked at 18:21:31: Kestrel bids £120.00 at 18:21:34", enabled = false),
            locked.edit,
        )
        assertEquals("10 seconds to the bid", locked.announcement)
    }

    @Test
    fun theLiveRegionSpeaksOnlyAtTheListedMoments() {
        val early = fixture.canonFinalMinute.copy(now = CatalogueFixture.at("2025-11-09T18:21:03Z")).lotState()
        assertNull(early.announcement)
        // 18:21:04 is 30 s before the 18:21:34 bid.
        assertEquals("30 seconds to the bid", fixture.canonFinalMinute.lotState().announcement)
        assertEquals("Bid sent", fixture.canonBidSent.lotState().announcement)
        assertEquals(
            "eBay replied: Your current bid puts you in the lead.",
            fixture.canonInTheLead.lotState().announcement,
        )
        assertNull(LotFixtures.canonArmedToday.lotState().announcement)
    }

    @Test
    fun everyAttentionCaseHasItsCardAndAction() {
        val cases =
            mapOf(
                fixture.omegaOverMax to LotAction.RAISE_MAX,
                fixture.canonSignInNeeded to LotAction.SIGN_IN,
                fixture.canonCaptcha to LotAction.OPEN_EBAY_PAGE,
                fixture.canonPhoneCheck to LotAction.OPEN_EBAY_PAGE,
                // The dry run met eBay's sign-in page, so its fix is signing in.
                fixture.canonDryRunFailed to LotAction.SIGN_IN,
                fixture.canonUnreachable to LotAction.OPEN_ON_EBAY,
                fixture.omegaNeedsYou to LotAction.OPEN_EBAY_PAGE,
            )
        cases.forEach { (moment, action) ->
            val card = moment.lotState().card
            assertEquals(moment.status.kind.name, StatusTone.Attention, card?.tone)
            assertEquals(moment.status.kind.name, action, card?.action)
        }
        assertEquals(
            "Solve it before 18:21:34 so Kestrel can bid. Kestrel never solves these for you.",
            fixture.canonCaptcha
                .lotState()
                .card!!
                .detail,
        )
        val offline = fixture.canonOffline.lotState()
        // The banner says the phone is offline once; the card says what that does to the bid.
        assertEquals("The 18:21:34 bid can’t go out", offline.card!!.title.text)
        assertEquals("Kestrel bids once the phone reconnects.", offline.card!!.detail)
        assertEquals(OfflineNote("Offline since 18:02", "Prices are from 18:02."), offline.offline)
        // Raising is the card's action, so there is no second edit row.
        assertNull(fixture.omegaOverMax.lotState().edit)
    }

    @Test
    fun beforeTheEndTheRedCardOffersNoReceiptThatWouldOnlyLeadBackHere() {
        // S4 shows only once the auction has ended (LotPageBuilder.showsResult), and S3 is the receipt until then.
        val failed = fixture.canonBidFailed
        val running = failed.copy(now = Instant.ofEpochMilli(failed.snipe.endTimeUtcMillis - 2_000))
        assertTrue(running.page() is LotPage.Live)
        val card = running.lotState().card!!
        assertEquals(StatusTone.Failed, card.tone)
        assertNull(card.action)
    }

    @Test
    fun onlyTheTwoFailuresAreRed() {
        val notSent = LotFixtures.canonBidNotSentWhileRunning.lotState()
        assertEquals(StatusTone.Failed, notSent.card!!.tone)
        assertEquals(LotAction.SIGN_IN, notSent.card!!.action)
        listOf(
            fixture.canonFinalMinute,
            fixture.canonBidSent,
            fixture.canonOutbid,
            fixture.omegaOverMax,
            fixture.canonCaptcha,
            fixture.canonOffline,
        ).forEach { assertTrue(it.status.kind.name, it.lotState().card?.tone != StatusTone.Failed) }
    }

    @Test
    fun watchingOffersASetMaxAndNoStopSniping() {
        val state = fixture.polaroidWatching.lotState()
        assertEquals(EditRowState(LotAction.SET_MAX, null, enabled = true), state.edit)
        assertNull(state.menu.stopSniping)
        assertTrue(state.menu.canDelete)
        assertEquals("Postage", state.money.endLabel)
        assertEquals("Watching. Set a max to snipe it.", state.note)
        assertTrue(state.times.rows.none { it.label == "Bids" })
    }

    @Test
    fun theBidWindowKeepsDeleteAwayButStopSnipingStays() {
        val state = fixture.canonFinalMinute.lotState()
        assertFalse(state.menu.canDelete)
        assertEquals(MenuEntry("Stop sniping", enabled = true), state.menu.stopSniping)
        assertTrue(
            LotFixtures.canonArmedToday
                .lotState()
                .menu.canDelete,
        )
    }

    @Test
    fun theResultReplacesTheLivePageOnceItIsKnown() {
        assertTrue(fixture.canonFinalMinute.page() is LotPage.Live)
        assertTrue(fixture.canonReadingResult.page() is LotPage.Live)
        assertTrue(fixture.canonWon.page() is LotPage.Finished)
        assertTrue(fixture.nintendo64Lost.page() is LotPage.Finished)
        // A bid that was not sent keeps its red card until the auction ends.
        assertTrue(LotFixtures.canonBidNotSentWhileRunning.page() is LotPage.Live)
        assertTrue(fixture.canonBidNotSent.page() is LotPage.Finished)
        // A result Kestrel could not read is shown as unknown, not as a wait that never ends.
        assertTrue(LotFixtures.canonResultUnknown.page() is LotPage.Finished)
    }

    @Test
    fun theEditSheetsHaveTheirWordsAndLimits() {
        val edit = LotFixtures.canonEditing.lotState(SheetRequest(SheetKind.Edit)).sheet!!
        assertEquals("Edit your max", edit.title)
        assertEquals("£96.00 now · ends 18:21:40 GMT", edit.lotLine)
        assertEquals(CatalogueFixture.gbp("98.00"), edit.minimum)
        assertEquals("Change it until 18:21:31, 3\u202Fs before the bid.", edit.deadline)
        assertNull(edit.locked)
        assertEquals("Save max", edit.primary)

        val raise = LotFixtures.omegaRaising.lotState(SheetRequest(SheetKind.Raise)).sheet!!
        assertEquals("Raise your max", raise.title)
        assertEquals(CatalogueFixture.gbp("2200.00"), raise.minimum)
        assertEquals("Bids at 21:02:09 GMT", raise.deadline)
        assertEquals("Raise max", raise.primary)

        val set = fixture.polaroidWatching.lotState(SheetRequest(SheetKind.Set, leadSeconds = 6)).sheet!!
        assertEquals("Set your max", set.title)
        assertEquals(CatalogueFixture.gbp("63.00"), set.minimum)
        assertEquals(listOf(3, 6, 10), set.leadOptions)
        assertEquals(6, set.selectedLead)
        assertEquals("Arm snipe", set.primary)

        val locked = LotFixtures.canonLocked.lotState(SheetRequest(SheetKind.Edit)).sheet!!
        assertEquals(
            "The bid window is open, so this can’t change now. Kestrel bids £120.00 at 18:21:34.",
            locked.locked,
        )
    }

    @Test
    fun theChipIsAlwaysTheStatusModelsOwnWords() {
        listOf(
            fixture.canonFinalMinute,
            fixture.canonBidSent,
            fixture.canonInTheLead,
            fixture.canonOutbid,
            fixture.walkmanExtended,
            fixture.canonReadingResult,
            fixture.omegaOverMax,
            fixture.canonCaptcha,
            fixture.polaroidWatching,
        ).forEach { moment ->
            val state = moment.lotState()
            assertEquals(moment.status, state.status)
            assertEquals(moment.status.chipText(), state.chipText)
        }
    }
}
