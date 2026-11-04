package app.kestrel.ui.model

import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.theme.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The words of every status against the copy deck (DESIGN 6.1), for the fixture moments. */
class LotStatusWordsTest {
    private val fixture = CatalogueFixture
    private val thin = '\u202F'

    private fun LotMoment.labelText() = status.label(now).text

    private fun LotMoment.detailText() = status.detail(now)

    @Test
    fun watching() {
        val moment = fixture.polaroidWatching
        assertEquals("Watching · no max yet", moment.labelText())
        assertEquals("Watching. Set a max to snipe it.", moment.detailText())
        assertEquals(LotAction.SET_MAX, moment.status.action(moment.now))
    }

    @Test
    fun armed() {
        assertEquals("Armed · max £45.00", fixture.miniDiscArmed.labelText())
        assertEquals("Armed · bids at 18:21:34, 6${thin}s before the end", fixture.canonArmed.detailText())
        assertEquals("Armed · bids Mon 10 Nov at 20:09, 6${thin}s before the end", fixture.miniDiscArmed.detailText())
        assertEquals(LotAction.EDIT_MAX, fixture.canonArmed.status.action(fixture.canonArmed.now))
    }

    @Test
    fun overMax() {
        val moment = fixture.omegaOverMax
        assertEquals("£250.00 over your £1,900.00 max", moment.labelText())
        assertEquals(
            "The price is £2,150.00, £250.00 over your max. Raise it or stop sniping.",
            moment.detailText(),
        )
        assertEquals(LotAction.RAISE_MAX, moment.status.action(moment.now))
    }

    @Test
    fun overMaxWhenOnlyEbaysNextBidPassesTheMax() {
        val status = LotStatus.OverMax(fixture.gbp("119.00"), fixture.gbp("120.00"), fixture.gbp("121.00"))
        assertEquals("Next bid £121.00 is over your £120.00 max", status.label(fixture.now).text)
    }

    @Test
    fun signInNeeded() {
        val moment = fixture.canonSignInNeeded
        assertEquals("Sign-in needed · paused", moment.labelText())
        assertEquals("eBay signed you out. Sign in before 18:21:34 so Kestrel can bid.", moment.detailText())
        assertEquals(LotAction.SIGN_IN, moment.status.action(moment.now))
    }

    @Test
    fun captcha() {
        val moment = fixture.canonCaptcha
        assertEquals("eBay wants a quick check", moment.labelText())
        assertEquals(
            "Solve it on eBay’s page before 18:21:34. Kestrel never solves these for you.",
            moment.detailText(),
        )
        assertEquals(LotAction.OPEN_EBAY_PAGE, moment.status.action(moment.now))
    }

    @Test
    fun phoneCheck() {
        val moment = fixture.canonPhoneCheck
        assertEquals("eBay wants to verify you", moment.labelText())
        assertEquals(
            "eBay asked for a code by text. Enter it on eBay’s page; Kestrel will retry the dry run.",
            moment.detailText(),
        )
    }

    @Test
    fun dryRunFailed() {
        val moment = fixture.canonDryRunFailed
        assertEquals("Dry run failed", moment.labelText())
        assertEquals(
            "At 18:11:40 eBay showed a sign-in page instead of the bid panel. Fix it before 18:21:34.",
            moment.detailText(),
        )
        assertEquals(LotAction.FIX, moment.status.action(moment.now))
    }

    @Test
    fun offlineNearBid() {
        val moment = fixture.canonOffline
        assertEquals("Phone offline · bid at risk", moment.labelText())
        assertEquals("Kestrel can’t bid at 18:21:34 unless the phone reconnects.", moment.detailText())
        assertNull(moment.status.action(moment.now))
    }

    @Test
    fun unreachable() {
        val moment = fixture.canonUnreachable
        assertEquals("Can’t reach the listing", moment.labelText())
        assertEquals("Kestrel couldn’t check this listing three times. It keeps trying.", moment.detailText())
        assertEquals(LotAction.OPEN_ON_EBAY, moment.status.action(moment.now))
    }

    @Test
    fun needsYou() {
        val moment = fixture.omegaNeedsYou
        assertEquals("Needs you · open the listing", moment.labelText())
        assertEquals("eBay showed “Confirm your age” instead of the item. Open it to check.", moment.detailText())
    }

    @Test
    fun finalMinute() {
        val moment = fixture.canonFinalMinute
        assertEquals("Final minute · bids in 30${thin}s", moment.labelText())
        assertEquals(
            "Kestrel bids £120.00 in 30${thin}s, at 18:21:34, 6${thin}s before the end.",
            moment.detailText(),
        )
        assertEquals("Final minute, bids in 30 seconds", moment.status.spokenLabel(moment.now))
        assertEquals(LotAction.EDIT_MAX, moment.status.action(moment.now))
        assertNull(moment.status.action(CatalogueFixture.at("2025-11-09T18:21:31Z")))
    }

    @Test
    fun bidSentAndNoReply() {
        assertEquals("Bid sent · waiting for eBay", fixture.canonBidSent.labelText())
        assertEquals("Bid sent at 18:21:33.9 · waiting for eBay’s reply", fixture.canonBidSent.detailText())
        assertEquals("Bid sent · no reply yet", fixture.canonNoReply.labelText())
        assertEquals(
            "No reply from eBay yet. Kestrel will read the result when the auction ends.",
            fixture.canonNoReply.detailText(),
        )
    }

    @Test
    fun inTheLead() {
        val moment = fixture.canonInTheLead
        assertEquals("In the lead · ends 18:21:40", moment.labelText())
        assertEquals(
            "eBay accepted your bid: “Your current bid puts you in the lead.” Not won until it ends.",
            moment.detailText(),
        )
    }

    @Test
    fun outbid() {
        val moment = fixture.canonOutbid
        assertEquals("Outbid · ends 18:21:40", moment.labelText())
        assertEquals(
            "eBay replied: “You’ve been outbid. Another bidder’s maximum is higher.” " +
                "Someone’s max is higher than yours.",
            moment.detailText(),
        )
    }

    @Test
    fun extended() {
        val moment = fixture.walkmanExtended
        assertEquals("Extended · now ends 19:56:42", moment.labelText())
        assertEquals("A late bid reset the clock to 60${thin}s. Kestrel is still following.", moment.detailText())
        assertEquals("Extended · now ends 19:56:42", moment.status.chipText())
    }

    @Test
    fun readingResult() {
        val moment = fixture.canonReadingResult
        assertEquals("Ended · reading the result", moment.labelText())
        assertEquals("Reading eBay’s result…", moment.detailText())
    }

    @Test
    fun won() {
        assertEquals("Won · Sat 8 Nov", fixture.gameAndWatchWon.labelText())
        assertEquals("You won · won for £101.00, £19.00 under your £120.00 max", fixture.canonWon.detailText())
        assertEquals(LotAction.VIEW_RECEIPT, fixture.canonWon.status.action(fixture.canonWon.now))
    }

    @Test
    fun lost() {
        val moment = fixture.nintendo64Lost
        assertEquals("Lost · Fri 7 Nov", moment.labelText())
        assertEquals("Outbid · sold for £79.00. Your max was £65.00.", moment.detailText())
    }

    @Test
    fun endedEarly() {
        val moment = fixture.gameBoyEndedEarly
        assertEquals("Ended early by the seller", moment.labelText())
        assertEquals("The seller ended the listing early. Nothing was bid.", moment.detailText())
    }

    @Test
    fun cancelledByYou() {
        val moment = fixture.gameBoyCancelled
        assertEquals("Cancelled · Sat 8 Nov", moment.labelText())
        assertEquals("You stopped this snipe. Nothing was bid.", moment.detailText())
    }

    @Test
    fun cancelledByGroup() {
        val moment = fixture.walkmanCancelledByGroup
        assertEquals("Cancelled · you won another in Portable audio", moment.labelText())
        assertEquals("Kestrel cancelled this because you won Sony MiniDisc Walkman MZ-N707.", moment.detailText())
        assertEquals(LotAction.UNDO_CANCEL, moment.status.action(moment.now))
    }

    @Test
    fun bidNotSent() {
        val moment = fixture.canonBidNotSent
        assertEquals("Bid not sent", moment.labelText())
        assertEquals("eBay signed you out at 18:21:20. Nothing was bid.", moment.detailText())
        assertEquals(LotAction.SIGN_IN, moment.status.action(moment.now))
    }

    @Test
    fun bidFailed() {
        val moment = fixture.canonBidFailed
        assertEquals("Bid failed", moment.labelText())
        assertEquals(
            "eBay didn’t accept the bid: “Your bid wasn’t placed. Please try again.”. Nothing was bid.",
            moment.detailText(),
        )
    }

    @Test
    fun onlyTheTwoFailuresAreRed() {
        val red = LotStatusKind.entries.filter { it.isFailure }
        assertEquals(listOf(LotStatusKind.BidNotSent, LotStatusKind.BidFailed), red)
        assertEquals(StatusTone.Failed, fixture.canonBidNotSent.status.tone)
        assertEquals(StatusTone.Ended, fixture.nintendo64Lost.status.tone)
        assertEquals(StatusTone.Won, fixture.canonWon.status.tone)
    }

    @Test
    fun attentionLabelsAreSpokenAsNeedingAttention() {
        assertEquals(
            "Needs attention: £250.00 over your £1,900.00 max",
            fixture.omegaOverMax.status.spokenLabel(fixture.now),
        )
    }
}
