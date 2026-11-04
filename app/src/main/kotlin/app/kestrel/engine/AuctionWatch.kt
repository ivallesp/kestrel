package app.kestrel.engine

import app.kestrel.model.AutoRefreshState
import app.kestrel.model.EbaySite
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money

/** A price and bid count; the minimum next bid depends on both. */
internal data class PriceLevel(
    val price: Money,
    val bidCount: Int,
)

/** What one AUTO_REFRESH reply changed. */
internal data class AuctionUpdate(
    val newLevel: PriceLevel?,
    /** The end moved later: Extended Bidding reset the clock. */
    val extendedFromUtcMillis: Long?,
    /** The end moved earlier, which a running auction should not do; noted, and the earlier end is trusted. */
    val movedEarlierFromUtcMillis: Long?,
    val ended: Boolean,
)

/** The live state of the auction during a window, fed by the item page and AUTO_REFRESH replies. */
internal class AuctionWatch(
    private val site: EbaySite,
    private val prices: PriceGuard,
    initialEndUtcMillis: Long,
) {
    var endUtcMillis: Long = initialEndUtcMillis
        private set
    var level: PriceLevel? = null
        private set
    var extensions: Int = 0
        private set

    /** The latest reply said the auction has ended. */
    var endedSeen: Boolean = false
        private set

    private var listingCurrency: String? = null
    private var buyerProtectionIncluded = false
    private var layerMinimum: Money? = null
    private var layerMinimumLevel: PriceLevel? = null

    fun startFrom(listing: Listing) {
        endUtcMillis = listing.endTimeUtcMillis
        level = PriceLevel(listing.currentPrice, listing.bidCount)
        listingCurrency = listing.currentPrice.currency
        buyerProtectionIncluded = listing.buyerProtectionFee != null
        endedSeen = listing.status == ListingStatus.ENDED
    }

    /** eBay's own minimum, read from the bid layer at the current price. */
    fun recordLayerMinimum(minimum: Money?) {
        if (minimum == null) return
        layerMinimum = minimum
        layerMinimumLevel = level
    }

    /** The "ended" replies were not confirmed: the auction is taken as running until a reply or a page says otherwise. */
    fun disregardEndedSignal() {
        endedSeen = false
    }

    /** A reload showed the auction still running with a later end than the replies did. */
    fun noteLaterEnd(newEndUtcMillis: Long) {
        if (newEndUtcMillis > endUtcMillis) {
            endUtcMillis = newEndUtcMillis
            extensions++
        }
        endedSeen = false
    }

    fun apply(state: AutoRefreshState): AuctionUpdate {
        var newLevel: PriceLevel? = null
        // A price in another currency (a converted display price) cannot be compared with the max, so it is ignored.
        val price = state.currentPrice?.takeIf { listingCurrency == null || it.currency == listingCurrency }
        if (price != null) {
            val candidate = PriceLevel(price, state.bidCount ?: level?.bidCount ?: 0)
            if (candidate != level) {
                level = candidate
                newLevel = candidate
            }
        }
        var extendedFrom: Long? = null
        var movedEarlierFrom: Long? = null
        val reportedEnd = state.endTimeUtcMillis
        if (reportedEnd != null && reportedEnd > endUtcMillis + END_CHANGE_THRESHOLD_MILLIS) {
            extendedFrom = endUtcMillis
            endUtcMillis = reportedEnd
            extensions++
        } else if (reportedEnd != null && reportedEnd < endUtcMillis - END_CHANGE_THRESHOLD_MILLIS) {
            movedEarlierFrom = endUtcMillis
            endUtcMillis = reportedEnd
        }
        endedSeen = state.ended
        return AuctionUpdate(newLevel, extendedFrom, movedEarlierFrom, state.ended)
    }

    /**
     * The best estimate of the lowest bid eBay accepts now: eBay's own figure from the layer while the price has not
     * moved since, otherwise the higher of that figure and the one computed from the latest price.
     */
    fun minimumNextBid(): Money? {
        val current = level
        val computed = current?.let { prices.minimumNextBid(site, it.price, it.bidCount, buyerProtectionIncluded) }
        val fromLayer = layerMinimum ?: return computed
        return if (layerMinimumLevel == current) fromLayer else higherOf(fromLayer, computed)
    }

    private companion object {
        // AUTO_REFRESH end times have one-second precision.
        const val END_CHANGE_THRESHOLD_MILLIS = 1_000L
    }
}
