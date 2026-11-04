package app.kestrel.engine

import app.kestrel.ebay.BidIncrements
import app.kestrel.model.EbaySite
import app.kestrel.model.Listing
import app.kestrel.model.Money

/** eBay's minimum next bid, injected so the engine can be tested without the increment tables. */
internal fun interface MinimumBidRule {
    fun minimumNextBid(
        site: EbaySite,
        current: Money,
        bidCount: Int,
        buyerProtectionIncluded: Boolean,
    ): Money

    companion object {
        val Ebay = MinimumBidRule(BidIncrements::minimumNextBid)
    }
}

/** Decides whether the user's max can still win. */
internal class PriceGuard(
    private val rule: MinimumBidRule,
) {
    /** The lowest bid eBay would accept now, or null when the rule cannot tell. */
    fun minimumNextBid(
        site: EbaySite,
        price: Money,
        bidCount: Int,
        buyerProtectionIncluded: Boolean,
    ): Money? =
        try {
            rule.minimumNextBid(site, price, bidCount, buyerProtectionIncluded)
        } catch (error: Exception) {
            null
        }

    fun minimumNextBid(listing: Listing): Money? =
        minimumNextBid(listing.ref.site, listing.currentPrice, listing.bidCount, listing.buyerProtectionFee != null)

    /**
     * True when no bid at or under [max] can be accepted, false when one can, null when the amounts are in different
     * currencies and cannot be compared. Without a known minimum only the price itself can prove the max is too low.
     */
    fun isOverMax(
        minimum: Money?,
        price: Money?,
        bidCount: Int?,
        max: Money,
    ): Boolean? {
        if (minimum != null) return minimum.isAbove(max)
        if (price == null) return false
        val comparison = price.compareSafely(max) ?: return null
        return comparison > 0 || (comparison == 0 && (bidCount ?: 0) > 0)
    }
}
