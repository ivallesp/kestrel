package app.kestrel.data

import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus

/** A fixed "now" for tests: Sun 5 Nov 2023 21:00:00 UTC. */
const val TEST_NOW = 1_699_218_000_000L
const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE

fun gbp(major: String): Money = Money.of(major, "GBP")

/** Plain formatting for tests, so expected texts do not depend on the eBay lane's formatter. */
fun testFormatMoney(money: Money): String = "${money.currency} ${money.major.toPlainString()}"

fun testListing(
    itemId: String = "126256387730",
    site: EbaySite = EbaySite.UK,
    title: String = "Canon AE-1 Program 35mm film camera",
    endTimeUtcMillis: Long = TEST_NOW + 3 * HOUR,
    currentPrice: Money = gbp("31.50"),
    bidCount: Int = 4,
    isAuction: Boolean = true,
    status: ListingStatus = ListingStatus.ACTIVE,
    relistedItemId: String? = null,
    fetchedAtUtcMillis: Long = TEST_NOW - MINUTE,
) = Listing(
    ref = ItemRef(site, itemId),
    title = title,
    imageUrl = "https://i.ebayimg.com/images/g/abc/s-l1600.jpg",
    sellerName = "camera_shop",
    isAuction = isAuction,
    status = status,
    endTimeUtcMillis = endTimeUtcMillis,
    scheduledEndUtcMillis = endTimeUtcMillis,
    currentPrice = currentPrice,
    currentPriceInSiteCurrency = null,
    bidCount = bidCount,
    hasReserve = false,
    reserveMet = null,
    postage = gbp("4.99"),
    buyerProtectionFee = null,
    buyItNow = null,
    extendedBidding = false,
    userIsHighBidder = null,
    userBiddingStatus = null,
    endedText = null,
    endedEarly = false,
    signedIn = true,
    relistedItemId = relistedItemId,
    fetchedAtUtcMillis = fetchedAtUtcMillis,
)

fun testSnipe(
    id: Long = 1L,
    status: SnipeStatus = SnipeStatus.ARMED,
    maxBid: Money? = gbp("48.00"),
    endTimeUtcMillis: Long = TEST_NOW + 3 * HOUR,
    lastPrice: Money? = gbp("31.50"),
    lastBidCount: Int? = 4,
    lastCheckedAtUtcMillis: Long? = TEST_NOW - 10 * MINUTE,
    leadSeconds: Int = 6,
    title: String = "Canon AE-1 Program",
    imageUrl: String? = null,
) = Snipe(
    id = id,
    ref = ItemRef(EbaySite.UK, "1262563877$id"),
    title = title,
    imageUrl = imageUrl,
    sellerName = "camera_shop",
    maxBid = maxBid,
    leadSeconds = leadSeconds,
    groupId = null,
    status = status,
    endTimeUtcMillis = endTimeUtcMillis,
    lastPrice = lastPrice,
    lastBidCount = lastBidCount,
    lastCheckedAtUtcMillis = lastCheckedAtUtcMillis,
    postage = null,
    buyerProtectionFee = null,
    buyItNow = null,
    hasReserve = false,
    reserveMet = null,
    extendedBidding = false,
    dryRunAtUtcMillis = null,
    dryRunPassed = null,
    result = null,
    createdAtUtcMillis = TEST_NOW - HOUR,
    updatedAtUtcMillis = TEST_NOW - HOUR,
)
