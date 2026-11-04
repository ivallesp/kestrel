package app.kestrel.engine

import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus

/** Statuses that mean a bid already went out, so no window may send another one. */
internal val BID_SENT_STATUSES =
    setOf(SnipeStatus.BID_SENT, SnipeStatus.LEADING, SnipeStatus.OUTBID, SnipeStatus.EXTENDED)

/** An end this much before the schedule is an early end, not clock or rounding noise. */
internal const val EARLY_END_MARGIN_MILLIS = 60_000L

private val itemNumberInUrl = Regex("/itm/(?:[^/?#]+/)?(\\d{9,15})(?=[/?#]|$)")

/** Copies what the item page said into the snipe. Fields the page left out keep their last known value. */
internal fun Snipe.withListing(
    listing: Listing,
    nowUtcMillis: Long,
): Snipe =
    copy(
        title = listing.title.ifBlank { title },
        imageUrl = listing.imageUrl ?: imageUrl,
        sellerName = listing.sellerName ?: sellerName,
        endTimeUtcMillis = if (listing.endTimeUtcMillis > 0) listing.endTimeUtcMillis else endTimeUtcMillis,
        lastPrice = listing.currentPrice,
        lastBidCount = listing.bidCount,
        lastCheckedAtUtcMillis = nowUtcMillis,
        postage = listing.postage ?: postage,
        buyerProtectionFee = listing.buyerProtectionFee,
        // Buy It Now disappears after the first bid, so a missing price is news, not a gap.
        buyItNow = listing.buyItNow,
        hasReserve = listing.hasReserve,
        reserveMet = listing.reserveMet,
        extendedBidding = listing.extendedBidding,
        updatedAtUtcMillis = nowUtcMillis,
    )

/** True when the listing ended well before its schedule, or eBay says the seller ended it. */
internal fun Listing.endedBeforeSchedule(knownEndUtcMillis: Long): Boolean {
    if (status != ListingStatus.ENDED) return false
    if (endedEarly) return true
    val scheduledEnd = scheduledEndUtcMillis ?: knownEndUtcMillis
    return endTimeUtcMillis < scheduledEnd - EARLY_END_MARGIN_MILLIS
}

/**
 * The item number eBay showed instead of [requested], when the page is about another listing (relisted or
 * redirected). Its price and end time must then not be used for the snipe.
 */
internal fun Listing.otherItemThan(
    requested: ItemRef,
    pageUrl: String,
): String? {
    if (ref.itemId != requested.itemId) return ref.itemId
    val numberInUrl = itemNumberInUrl.find(pageUrl)?.groupValues?.get(1)
    if (numberInUrl != null && numberInUrl != requested.itemId) return numberInUrl
    val relisted = relistedItemId
    // An ended page can name the relisted item while still describing the original listing; only a running listing
    // under another number is the new one.
    if (relisted != null && relisted != requested.itemId && status == ListingStatus.ACTIVE) return relisted
    return null
}
