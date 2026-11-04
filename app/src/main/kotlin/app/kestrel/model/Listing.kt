package app.kestrel.model

/** What eBay's item page said about a listing at [fetchedAtUtcMillis]. All times are UTC epoch milliseconds. */
data class Listing(
    val ref: ItemRef,
    val title: String,
    val imageUrl: String?,
    val sellerName: String?,
    val isAuction: Boolean,
    val status: ListingStatus,
    /** Current end time. Moves under Extended Bidding. */
    val endTimeUtcMillis: Long,
    /** The end the listing was scheduled with (refreshTimers.itemEnding); differs from [endTimeUtcMillis] if it ended early. */
    val scheduledEndUtcMillis: Long?,
    /** Current price in the listing's own currency (the currency a bid must be placed in). */
    val currentPrice: Money,
    /** Current price converted to the viewing site's currency, when the listing is from another site. */
    val currentPriceInSiteCurrency: Money?,
    val bidCount: Int,
    val hasReserve: Boolean,
    /** Null when unknown; false when eBay says the reserve is not met. */
    val reserveMet: Boolean?,
    val postage: Money?,
    /** UK private sellers: the Buyer Protection fee already included in [currentPrice]. */
    val buyerProtectionFee: Money?,
    val buyItNow: Money?,
    val extendedBidding: Boolean,
    /** Signed-in user's standing, when eBay exposes it. */
    val userIsHighBidder: Boolean?,
    /** eBay's own field, for example WINNING or OUTBID; null when signed out or unknown. */
    val userBiddingStatus: String?,
    /** The ended banner text, for example "Item sold on Sun, 14 Dec at 5:35 PM." */
    val endedText: String?,
    val endedEarly: Boolean,
    /** Null when the page did not say. */
    val signedIn: Boolean?,
    /** Item number eBay redirected to (relisted), when different from [ref]. */
    val relistedItemId: String?,
    val fetchedAtUtcMillis: Long,
)

enum class ListingStatus { ACTIVE, ENDED }
