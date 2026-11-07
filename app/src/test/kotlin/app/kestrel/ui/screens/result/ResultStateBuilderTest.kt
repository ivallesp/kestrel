package app.kestrel.ui.screens.result

import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.format.NO_BREAK_SPACE
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.screens.lot.LotFixtures
import app.kestrel.ui.screens.lot.LotFixtures.resultState
import app.kestrel.ui.theme.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4's words (DESIGN S4, 3.14, 3.15) for every kind of result, and the shared text receipt. */
class ResultStateBuilderTest {
    private val fixture = CatalogueFixture

    /** The status line as it reads, the no-break spaces that keep the day and the time whole shown as spaces. */
    private fun ResultUiState.line(): String = statusLine.text.replace(NO_BREAK_SPACE, ' ')

    @Test
    fun aWinIsWonForWithTheTotalAndNeverPaid() {
        val state = fixture.canonWon.resultState()
        assertEquals(ResultVariant.Won, state.variant)
        assertEquals("Won · ended today, 18:21:40 GMT", state.line())
        assertEquals("You won", state.headline)
        val paid = state.paid!!
        assertEquals("£101.00", paid.wonFor)
        assertEquals("£19.00 under", paid.under)
        assertEquals("your £120.00 max", paid.maxLine)
        assertEquals(listOf(LedgerLine("Final price", "£101.00"), LedgerLine("Postage", "£6.50")), paid.ledger)
        assertEquals(LedgerLine("Total", "£107.50"), paid.total)
        assertEquals(ResultStateBuilder.PAYMENT_NOTE, paid.note)
        assertTrue(state.stampSeal)
        // "You paid" is never said (rule MF22).
        assertFalse(paid.spoken.contains("paid", ignoreCase = true))
    }

    @Test
    fun eBaysReplyIsTheQuoteWithItsTimings() {
        val quote = fixture.canonWon.resultState().quote!!
        assertEquals("Sent 6.1\u202Fs before the end · reply in 0.8\u202Fs", quote.timing)
        assertEquals("“Your current bid puts you in the lead.”", quote.quote)
        assertEquals("eBay replied in 0.8 seconds: Your current bid puts you in the lead.", quote.spoken)
    }

    @Test
    fun aLossSaysTheBidCountedAndNothingIsOwed() {
        val state = fixture.nintendo64Lost.resultState()
        assertEquals(ResultVariant.Lost, state.variant)
        assertEquals("Lost · ended today, 19:41:00 GMT", state.line())
        assertEquals("Outbid", state.headline)
        val sold = state.sold!!
        assertEquals("Sold for", sold.figures.startLabel)
        assertEquals("£79.00", sold.figures.startValue)
        assertEquals("Your max", sold.figures.endLabel)
        assertEquals("£65.00", sold.figures.endValue)
        assertEquals("Outbid by someone with a higher max", sold.reason)
        assertEquals(
            "Your £65.00 bid reached eBay 6.0\u202Fs before the end and counted. Their max was higher, so eBay’s " +
                "proxy bidding kept them ahead. You pay nothing.",
            sold.explanation,
        )
        assertTrue(state.secondChance)
        assertEquals("“You’ve been outbid. Another bidder’s maximum is higher.”", state.quote!!.quote)
        // "Accepted" belongs only to the in-the-lead detail (rule MF21).
        assertFalse(sold.explanation.contains("accepted"))
    }

    @Test
    fun aBidThatWasNotSentIsRedAndOffersSignIn() {
        val state = fixture.canonBidNotSent.resultState()
        assertEquals(ResultVariant.BidNotSent, state.variant)
        assertEquals(StatusTone.Failed, state.tone)
        assertEquals("Today, 18:21:34 GMT", state.line())
        assertEquals("Bid not sent", state.headline)
        assertEquals(
            OutcomeCardText(
                StatusTone.Failed,
                "eBay signed you out at 18:21:20, 14\u202Fs before the bid. Nothing was bid.",
                LotAction.SIGN_IN,
            ),
            state.outcome,
        )
        assertNull(state.quote)
    }

    @Test
    fun aRefusedBidQuotesEbay() {
        val state = fixture.canonBidFailed.resultState()
        assertEquals(ResultVariant.BidFailed, state.variant)
        assertEquals(StatusTone.Failed, state.tone)
        assertEquals("Bid failed", state.headline)
        assertEquals("Today, 18:21:34 GMT", state.line())
        assertTrue(state.outcome!!.text.startsWith("eBay didn’t accept the bid: “Your bid wasn’t placed."))
    }

    @Test
    fun theOtherEndsAreCalmAndSayNothingWasBid() {
        val early = fixture.gameBoyEndedEarly.resultState()
        assertEquals(ResultVariant.EndedEarly, early.variant)
        assertEquals("Ended early", early.headline)
        // The headline names the state, so the line under the plate gives only the moment.
        assertEquals("", early.statusLine.words)
        assertEquals("The seller ended the listing before its end time. Nothing was bid.", early.outcome!!.text)
        assertEquals(StatusTone.Ended, early.tone)

        val cancelled = fixture.gameBoyCancelled.resultState()
        assertEquals("Cancelled", cancelled.headline)
        assertEquals("You stopped this snipe. Nothing was bid.", cancelled.outcome!!.text)
        assertEquals("Sat 8 Nov, 16:20 GMT", cancelled.line())

        val group = LotFixtures.walkmanSkippedByGroup.resultState()
        assertEquals("Cancelled", group.headline)
        assertEquals(
            "Kestrel cancelled this because you won Sony MiniDisc Walkman MZ-N707 in Portable audio.",
            group.outcome!!.text,
        )
        assertEquals("Today, 20:10:16 GMT", group.line())
        // The Walkman's auction still runs, so the card offers to bid on it again.
        assertEquals(LotAction.UNDO_CANCEL, group.outcome!!.action)
    }

    @Test
    fun anUnreadResultSaysSoAndSendsTheUserToEbay() {
        val state = LotFixtures.canonResultUnknown.resultState()
        assertEquals(ResultVariant.Unknown, state.variant)
        assertEquals("Result not known", state.headline)
        assertEquals(LotAction.OPEN_ON_EBAY, state.outcome!!.action)
        assertTrue(state.outcome!!.text.startsWith("Kestrel sent your £120.00 bid 6.1\u202Fs before the end"))
    }

    @Test
    fun theTextReceiptHasTheTitleTheMoneyTheEndAndEbaysWords() {
        assertEquals(
            listOf(
                "Canon AE-1 Program + 50mm f/1.8",
                "Won for £101.00",
                "Total with postage £107.50",
                "Ended Sun 9 Nov, 18:21:40 GMT",
                "eBay replied: “Your current bid puts you in the lead.”",
            ).joinToString("\n"),
            fixture.canonWon.resultState().shareText,
        )
        assertEquals(
            listOf(
                "Nintendo 64 console",
                "Lost · sold for £79.00",
                "Your max £65.00",
                "Ended Fri 7 Nov, 19:41:00 GMT",
                "eBay replied: “You’ve been outbid. Another bidder’s maximum is higher.”",
            ).joinToString("\n"),
            fixture.nintendo64Lost.resultState().shareText,
        )
    }

    @Test
    fun theSealStampsOnlyWhenAskedAndOnlyForAWin() {
        assertFalse(fixture.canonWon.resultState(stampSeal = false).stampSeal)
        assertFalse(fixture.nintendo64Lost.resultState(stampSeal = true).stampSeal)
    }

    @Test
    fun theLossPlaysOnTheFirstOpenOfAConfirmedLossOnly() {
        assertTrue(fixture.nintendo64Lost.resultState(stampSeal = true).mourn)
        assertFalse(fixture.nintendo64Lost.resultState(stampSeal = false).mourn)
        assertFalse("a win is not mourned", fixture.canonWon.resultState(stampSeal = true).mourn)
    }

    @Test
    fun aResultFirstOpenedMoreThanAWeekAfterItsEndPlaysNoScene() {
        val week = ResultStateBuilder.SCENE_FRESH_FOR

        fun LotMoment.later(by: java.time.Duration) = copy(now = now + by)
        assertTrue(
            fixture.nintendo64Lost
                .later(week.minusMinutes(5))
                .resultState(stampSeal = true)
                .mourn,
        )
        assertFalse(
            fixture.nintendo64Lost
                .later(week.plusHours(2))
                .resultState(stampSeal = true)
                .mourn,
        )
        assertTrue(
            fixture.canonWon
                .later(week.minusMinutes(5))
                .resultState(stampSeal = true)
                .stampSeal,
        )
        assertFalse(
            fixture.canonWon
                .later(week.plusHours(2))
                .resultState(stampSeal = true)
                .stampSeal,
        )
    }
}
