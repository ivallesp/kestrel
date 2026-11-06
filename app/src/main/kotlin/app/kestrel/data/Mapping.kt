package app.kestrel.data

import app.kestrel.model.ItemRef
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeResult
import app.kestrel.model.TimelineEvent

/** One observed price of a listing, for the price chart. */
data class PricePoint(
    val atUtcMillis: Long,
    val price: Money,
    val bidCount: Int,
)

// Snipes added before 1.0.6 kept the site of their link (ebay.com, ebay.de...). Kestrel now opens every listing on the
// home site, where it is signed in, so a stored row is always read as the home site's.
internal fun SnipeEntity.toSnipe(): Snipe =
    Snipe(
        id = id,
        ref = ItemRef(site, itemId).onHomeSite(),
        title = title,
        imageUrl = imageUrl,
        sellerName = sellerName,
        maxBid = maxBid,
        leadSeconds = leadSeconds,
        groupId = groupId,
        status = status,
        endTimeUtcMillis = endTimeUtcMillis,
        lastPrice = lastPrice,
        lastBidCount = lastBidCount,
        lastCheckedAtUtcMillis = lastCheckedAtUtcMillis,
        postage = postage,
        buyerProtectionFee = buyerProtectionFee,
        buyItNow = buyItNow,
        hasReserve = hasReserve,
        reserveMet = reserveMet,
        extendedBidding = extendedBidding,
        dryRunAtUtcMillis = dryRunAtUtcMillis,
        dryRunPassed = dryRunPassed,
        result =
            resultOutcome?.let { outcome ->
                SnipeResult(
                    outcome = outcome,
                    finalPrice = resultFinalPrice,
                    bidSentAtUtcMillis = resultBidSentAtUtcMillis,
                    bidLeadMillis = resultBidLeadMillis,
                    replyLatencyMillis = resultReplyLatencyMillis,
                    ebayReplyText = resultEbayReplyText,
                    reason = resultReason,
                )
            },
        createdAtUtcMillis = createdAtUtcMillis,
        updatedAtUtcMillis = updatedAtUtcMillis,
    )

internal fun Snipe.toEntity(deletedAtUtcMillis: Long?): SnipeEntity =
    SnipeEntity(
        id = id,
        site = ref.site,
        itemId = ref.itemId,
        title = title,
        imageUrl = imageUrl,
        sellerName = sellerName,
        maxBid = maxBid,
        leadSeconds = leadSeconds,
        groupId = groupId,
        status = status,
        endTimeUtcMillis = endTimeUtcMillis,
        lastPrice = lastPrice,
        lastBidCount = lastBidCount,
        lastCheckedAtUtcMillis = lastCheckedAtUtcMillis,
        postage = postage,
        buyerProtectionFee = buyerProtectionFee,
        buyItNow = buyItNow,
        hasReserve = hasReserve,
        reserveMet = reserveMet,
        extendedBidding = extendedBidding,
        dryRunAtUtcMillis = dryRunAtUtcMillis,
        dryRunPassed = dryRunPassed,
        resultOutcome = result?.outcome,
        resultFinalPrice = result?.finalPrice,
        resultBidSentAtUtcMillis = result?.bidSentAtUtcMillis,
        resultBidLeadMillis = result?.bidLeadMillis,
        resultReplyLatencyMillis = result?.replyLatencyMillis,
        resultEbayReplyText = result?.ebayReplyText,
        resultReason = result?.reason,
        createdAtUtcMillis = createdAtUtcMillis,
        updatedAtUtcMillis = updatedAtUtcMillis,
        deletedAtUtcMillis = deletedAtUtcMillis,
    )

internal fun GroupEntity.toGroup(): SnipeGroup = SnipeGroup(id = id, name = name, targetWins = targetWins)

internal fun TimelineEventEntity.toEvent(): TimelineEvent =
    TimelineEvent(
        id = id,
        snipeId = snipeId,
        atUtcMillis = atUtcMillis,
        kind = kind,
        message = message,
        detail = detail,
    )

internal fun PricePointEntity.toPricePoint(): PricePoint =
    PricePoint(atUtcMillis = atUtcMillis, price = price, bidCount = bidCount)
