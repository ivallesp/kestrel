package app.kestrel.ui.screens.lot

import app.kestrel.ui.components.TimelineMoment
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.screens.lot.LotFixtures.lotState
import app.kestrel.ui.screens.lot.LotFixtures.resultState
import app.kestrel.ui.theme.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The S3 timeline and the S4 receipt (DESIGN 3.10, S3 9, S4 8), read from the events the engine writes. */
class LotTimelineTest {
    private val fixture = CatalogueFixture

    private fun LotMoment.rows(): List<Triple<String, String, String?>> =
        lotState().timeline.map { Triple(it.time, it.title, it.detail) }

    private fun LotMoment.receipt(): List<Triple<String, String, String?>> =
        resultState().receipt.map { Triple(it.time, it.title, it.detail) }

    @Test
    fun theFinalMinuteTimelineIsTheDesignsOwn() {
        assertEquals(
            listOf(
                Triple("18:21:40", "Auction ends", "Kestrel keeps following if it extends"),
                Triple("18:21:34", "Kestrel bids £120.00", "Your max, 6\u202Fs before the end"),
                // "Now 18:21:04" alone: the chip and the card already say the status.
                Triple("18:21:04", "", null),
                Triple("18:20:51", "Price rose to £96.00", "16 bids"),
                Triple("18:20:40", "Final minute", "Checking every 2\u202Fs"),
                Triple("18:11:40", "Dry run passed", "Bid panel opened, £120.00 typed, nothing sent"),
                Triple("09:14:02", "Armed", "£120.00 max, 6\u202Fs\u00A0lead"),
            ),
            fixture.canonFinalMinute.rows(),
        )
        val moments =
            fixture.canonFinalMinute
                .lotState()
                .timeline
                .map { it.moment }
        assertEquals(
            listOf(TimelineMoment.Future, TimelineMoment.Future, TimelineMoment.Now) + List(4) { TimelineMoment.Past },
            moments,
        )
    }

    @Test
    fun theBidAndTheReplyAreInTenths() {
        val rows = fixture.canonInTheLead.rows()
        assertTrue(rows.contains(Triple("18:21:34.7", "eBay replied: in the lead", null)))
        assertTrue(rows.contains(Triple("18:21:33.9", "Bid of £120.00 sent", "6.1\u202Fs before the end")))
        val bid =
            fixture.canonInTheLead
                .lotState()
                .timeline
                .first { it.title.startsWith("Bid of") }
        assertEquals(StatusTone.Bidding, bid.tone)
        assertEquals("18:21:33.9, Bid of £120.00 sent, 6.1 seconds before the end", bid.spoken)
    }

    @Test
    fun anExtensionIsShownAtTheBidThatResetTheClock() {
        assertTrue(
            fixture.walkmanExtended.rows().contains(
                Triple("19:55:42", "Kestrel’s bid reset the clock to 60\u202Fs", "Now ends 19:56:42"),
            ),
        )
    }

    @Test
    fun theWonReceiptSeparatesTheEndFromTheConfirmedWin() {
        assertEquals(
            listOf(
                Triple("18:21:46.2", "Won at £101.00", "Confirmed on the listing page"),
                Triple("18:21:40.0", "Auction ended", "On time, no extension"),
                Triple("18:21:34.7", "eBay replied in 0.8\u202Fs", "In the lead"),
                Triple("18:21:33.9", "Bid of £120.00 sent", "6.1\u202Fs before the end"),
                Triple("18:20:40.0", "Final minute", "Checking every 2\u202Fs"),
                Triple("18:11:40.2", "Dry run passed", "Nothing was sent"),
                Triple("09:14:02.0", "Armed with a £120.00 max, 6\u202Fs\u00A0lead", null),
            ),
            fixture.canonWon.receipt(),
        )
        val won =
            fixture.canonWon
                .resultState()
                .receipt
                .first()
        assertEquals(StatusTone.Won, won.tone)
    }

    @Test
    fun theLostReceiptGoesBackToTheDayItWasArmed() {
        val receipt = fixture.nintendo64Lost.receipt()
        assertEquals(Triple("19:41:05.3", "Lost", "Sold for £79.00, confirmed on the listing page"), receipt.first())
        assertTrue(receipt.contains(Triple("19:40:54.9", "eBay replied in 0.9\u202Fs", "Outbid")))
        assertEquals(Triple("Wed 5 Nov 21:12:44", "Armed with a £65.00 max, 6\u202Fs\u00A0lead", null), receipt.last())
    }

    @Test
    fun aBidThatWasNotSentIsShownWhereTheBidWasDueInRed() {
        val receipt = fixture.canonBidNotSent.resultState().receipt
        val rows = receipt.map { Triple(it.time, it.title, it.detail) }
        assertEquals(Triple("18:21:40.0", "Auction ended", "Sold for £104.00 to another bidder"), rows[0])
        assertEquals(Triple("18:21:34.0", "Bid not sent", "Signed out"), rows[1])
        assertEquals(Triple("18:21:20.3", "eBay ended the session", null), rows[2])
        assertEquals(StatusTone.Failed, receipt[1].tone)
        // Red is only ever the failure itself.
        assertTrue(receipt.filter { it.tone == StatusTone.Failed }.size == 1)
    }

    @Test
    fun aGroupCancelIsNotTheUsersOwnCancel() {
        val receipt = LotFixtures.walkmanSkippedByGroup.receipt()
        assertEquals(
            // Tenths on the day of the cancel, as on every other receipt.
            Triple("20:10:16.4", "Cancelled", "You won “Sony MiniDisc Walkman MZ-N707” in Portable audio."),
            receipt.first(),
        )
        val own = fixture.gameBoyCancelled.receipt()
        assertTrue(own.contains(Triple("16:20:00.0", "You stopped this snipe", "Nothing was bid")))
    }

    @Test
    fun anEditedMaxReadsAsAChange() {
        assertTrue(
            LotFixtures.omegaRaised.rows().contains(Triple("18:08:52", "Max changed to £2,200.00", "Was £1,900.00")),
        )
    }

    @Test
    fun aWatchedLotOnlyPromisesTheEnd() {
        val rows = fixture.polaroidWatching.rows()
        assertEquals(Triple("Thu 13 Nov 19:30", "Auction ends", null), rows.first())
        assertEquals(Triple("Fri 7 Nov 08:15:40", "Watching", "Added without a max"), rows.last())
    }
}
