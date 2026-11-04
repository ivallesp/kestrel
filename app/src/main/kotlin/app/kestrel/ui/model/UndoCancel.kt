package app.kestrel.ui.model

import app.kestrel.data.AddResult
import app.kestrel.data.EditResult
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.Snipe

/**
 * "Undo cancel" for a snipe its group cancelled after another member was won (DESIGN S4 "Cancelled by group" and the
 * Group "After a win" rule), built from the snipe actions the app already has.
 *
 * The data layer has no un-cancel, so the listing is armed again as a new snipe with the old max and lead, and the
 * cancelled one is deleted. It goes back on its own, outside the group: the group already has its win, so its rule
 * would cancel the snipe again before it bids. The new snipe's own checks refresh the listing before the bid.
 */
object UndoCancel {
    /** The listing as Kestrel last read it, or null when it never read a price (nothing to arm against). */
    fun listingOf(
        snipe: Snipe,
        nowUtcMillis: Long,
    ): Listing? {
        val price = snipe.lastPrice ?: return null
        return Listing(
            ref = snipe.ref,
            title = snipe.title,
            imageUrl = snipe.imageUrl,
            sellerName = snipe.sellerName,
            isAuction = true,
            status = ListingStatus.ACTIVE,
            endTimeUtcMillis = snipe.endTimeUtcMillis,
            scheduledEndUtcMillis = null,
            currentPrice = price,
            currentPriceInSiteCurrency = null,
            bidCount = snipe.lastBidCount ?: 0,
            hasReserve = snipe.hasReserve,
            reserveMet = snipe.reserveMet,
            postage = snipe.postage,
            buyerProtectionFee = snipe.buyerProtectionFee,
            buyItNow = snipe.buyItNow,
            extendedBidding = snipe.extendedBidding,
            userIsHighBidder = null,
            userBiddingStatus = null,
            endedText = null,
            endedEarly = false,
            signedIn = null,
            relistedItemId = null,
            fetchedAtUtcMillis = snipe.lastCheckedAtUtcMillis ?: nowUtcMillis,
        )
    }

    /**
     * Arms [snipe] again through [add] (the listing, the max, the lead, no group) and then removes the cancelled one
     * through [delete]. The result carries the new snipe, or why it could not be armed.
     */
    suspend fun run(
        snipe: Snipe,
        nowUtcMillis: Long,
        add: suspend (Listing, Money?, Int) -> AddResult,
        delete: suspend (Long) -> EditResult,
    ): EditResult {
        if (snipe.endTimeUtcMillis <= nowUtcMillis) return EditResult.NotAllowed(ENDED)
        val listing = listingOf(snipe, nowUtcMillis) ?: return EditResult.NotAllowed(NO_PRICE)
        return when (val added = add(listing, snipe.maxBid, snipe.leadSeconds)) {
            is AddResult.Added -> {
                delete(snipe.id)
                EditResult.Done(added.snipe)
            }

            is AddResult.AlreadyAdded -> {
                EditResult.Done(added.snipe)
            }

            is AddResult.Rejected -> {
                EditResult.NotAllowed(added.reason)
            }
        }
    }

    /** The snackbar once it is armed again, with the lot's short name: "Sniping Sony Walkman again, on its own". */
    fun message(shortTitle: String): String = "Sniping $shortTitle again, on its own"

    const val ENDED = "This auction has ended, so the snipe can’t come back."
    const val NO_PRICE = "Kestrel never read a price for this listing, so it can’t arm it again."
}
