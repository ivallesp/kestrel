package app.kestrel.engine

import app.kestrel.model.ListingStatus
import app.kestrel.model.SnipeOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultResolverTest {
    private val max = gbp("48.00")
    private val leading = BidEvidence(sent = true, standing = BidStanding.LEADING)
    private val unconfirmed = BidEvidence(sent = true, standing = BidStanding.UNCONFIRMED)

    private fun ended(
        price: String,
        signedIn: Boolean? = null,
        userIsHighBidder: Boolean? = null,
        userBiddingStatus: String? = null,
        hasReserve: Boolean = false,
        reserveMet: Boolean? = null,
        bidCount: Int = 5,
        endedText: String? = "Item sold on Wed, 4 Nov at 1:48 PM.",
    ) = testListing(
        status = ListingStatus.ENDED,
        price = gbp(price),
        bidCount = bidCount,
        signedIn = signedIn,
        userIsHighBidder = userIsHighBidder,
        userBiddingStatus = userBiddingStatus,
        hasReserve = hasReserve,
        reserveMet = reserveMet,
        endedText = endedText,
    )

    private fun outcome(
        listing: app.kestrel.model.Listing,
        bid: BidEvidence = leading,
    ) = ResultResolver.resolve(listing, max, bid, END_UTC).outcome

    @Test
    fun finalPriceEqualToTheMaxIsNeverCalledAWin() {
        assertEquals(SnipeOutcome.UNKNOWN, outcome(ended("48.00")))
    }

    @Test
    fun finalPriceAboveTheMaxIsALoss() {
        assertEquals(SnipeOutcome.LOST, outcome(ended("48.01")))
    }

    @Test
    fun structuredStandingDecides() {
        assertEquals(SnipeOutcome.WON, outcome(ended("40.00", userBiddingStatus = "WINNING"), unconfirmed))
        assertEquals(SnipeOutcome.LOST, outcome(ended("40.00", userBiddingStatus = "OUTBID"), unconfirmed))
        assertEquals(SnipeOutcome.WON, outcome(ended("48.00", signedIn = true, userIsHighBidder = true)))
        assertEquals(SnipeOutcome.LOST, outcome(ended("48.00", signedIn = true, userIsHighBidder = false)))
    }

    @Test
    fun highBidderFlagCountsOnlyWhenSignedIn() {
        // isHighBidder is false on every signed-out page.
        assertEquals(SnipeOutcome.UNKNOWN, outcome(ended("48.00", signedIn = false, userIsHighBidder = false)))
    }

    @Test
    fun contradictionsGiveUnknown() {
        assertEquals(SnipeOutcome.UNKNOWN, outcome(ended("60.00", userBiddingStatus = "WINNING")))
        assertEquals(SnipeOutcome.UNKNOWN, outcome(ended("30.00", signedIn = true, userIsHighBidder = false)))
    }

    @Test
    fun belowTheMaxIsAWinOnlyAfterEbaySaidTheBidLed() {
        assertEquals(SnipeOutcome.WON, outcome(ended("30.00")))
        assertEquals(SnipeOutcome.UNKNOWN, outcome(ended("30.00"), unconfirmed))
        // With a reserve, only a sale (or a met reserve) makes the leading bid a winner.
        assertEquals(SnipeOutcome.UNKNOWN, outcome(ended("30.00", hasReserve = true, endedText = null)))
        assertEquals(SnipeOutcome.WON, outcome(ended("30.00", hasReserve = true)))
    }

    @Test
    fun reserveNotMetIsALoss() {
        assertEquals(SnipeOutcome.LOST, outcome(ended("30.00", hasReserve = true, reserveMet = false)))
        // Even the highest bidder does not win below the reserve, also when Kestrel did not bid.
        val manual = ended("30.00", signedIn = true, userIsHighBidder = true, hasReserve = true, reserveMet = false)
        assertEquals(SnipeOutcome.LOST, outcome(manual))
        assertEquals(
            SnipeOutcome.NOT_PLACED,
            outcome(manual, BidEvidence(sent = false, standing = BidStanding.UNKNOWN)),
        )
    }

    @Test
    fun highestBidderWithAnUnknownReserveIsNotYetAWin() {
        val unknownReserve =
            ended("30.00", signedIn = true, userIsHighBidder = true, hasReserve = true, endedText = null)
        assertEquals(SnipeOutcome.UNKNOWN, outcome(unknownReserve))
    }

    @Test
    fun noBidsOnTheListingMeansTheBidDidNotRegister() {
        assertEquals(SnipeOutcome.FAILED, outcome(ended("10.00", bidCount = 0)))
    }

    @Test
    fun runningListingIsUnknown() {
        assertEquals(SnipeOutcome.UNKNOWN, outcome(testListing(price = gbp("30.00"))))
    }

    @Test
    fun withoutABidAnEarlyEndIsEndedEarly() {
        val early = testListing(status = ListingStatus.ENDED, endUtc = END_UTC - 86_400_000, scheduledEndUtc = END_UTC)
        assertEquals(
            SnipeOutcome.ENDED_EARLY,
            outcome(early, BidEvidence(sent = false, standing = BidStanding.UNKNOWN)),
        )
        assertEquals(
            SnipeOutcome.NOT_PLACED,
            outcome(ended("30.00"), BidEvidence(sent = false, standing = BidStanding.UNKNOWN)),
        )
    }

    @Test
    fun bannerTextIsOnlyAHint() {
        assertEquals(
            SnipeOutcome.WON,
            outcome(ended("40.00", endedText = "Congratulations! You won this auction."), unconfirmed),
        )
        // Text never settles a tie at the max.
        assertEquals(
            SnipeOutcome.UNKNOWN,
            outcome(ended("48.00", endedText = "Congratulations! You won this auction."), unconfirmed),
        )
        assertEquals(
            SnipeOutcome.LOST,
            outcome(ended("40.00", endedText = "You didn't win this auction."), unconfirmed),
        )
        // A won message cannot beat a final price above the max.
        assertEquals(SnipeOutcome.LOST, outcome(ended("60.00", endedText = "You won this auction."), unconfirmed))
    }
}
