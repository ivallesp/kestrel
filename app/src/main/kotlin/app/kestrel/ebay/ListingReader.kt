package app.kestrel.ebay

import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import kotlinx.serialization.json.JsonObject

/**
 * Builds a [Listing] from an item page (full HTML or the kestrel.js reduced page). Implements [ListingParser].
 * Sources and fallbacks follow docs/ARCHITECTURE.md section 3; numbers always come from the model's value fields,
 * display text is read only for facts that have no value field (Buyer Protection, ended banner, reserve wording).
 */
internal object ListingReader {
    fun read(
        pageText: String,
        requested: ItemRef,
        fetchedAtUtcMillis: Long,
    ): Listing? {
        val sources = ItemPageSources(pageText)
        if (sources.semantic == null && sources.buyBox == null) return null
        val isAuction = isAuction(sources)
        val price = currentPrice(sources, isAuction) ?: return null
        val endTime = endTime(sources) ?: return null
        val scheduledEnd = parseUtcMillis(sources.semantic.path("refreshTimers", "itemEnding", "value").asText())
        val status = status(sources)
        val endedText = if (status == ListingStatus.ENDED) statusMessageText(sources) else null
        val signedIn = signedIn(pageText)
        val buyBoxText = sources.buyBox.collectTexts().joinToString(" ")
        val reserveMet = if (isAuction) reserveMet(buyBoxText) else null
        val itemId = requested.itemId
        return Listing(
            ref = requested,
            title = title(sources) ?: "eBay item ${sources.pageItemId ?: itemId}",
            imageUrl = imageUrl(sources),
            sellerName =
                sources.semantic
                    ?.get("sellerUserName")
                    .asText()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
            isAuction = isAuction,
            status = status,
            endTimeUtcMillis = endTime,
            scheduledEndUtcMillis = scheduledEnd,
            currentPrice = price.listing,
            currentPriceInSiteCurrency = price.site,
            bidCount = if (isAuction) bidCount(sources) else 0,
            hasReserve =
                isAuction && (sources.semantic?.get("hasReservePrice").asBoolean() == true || reserveMet != null),
            reserveMet = reserveMet,
            postage = postage(sources, requested.site, price.listing.currency),
            buyerProtectionFee = buyerProtectionFee(sources, isAuction, price.listing),
            buyItNow =
                sources.buyBox
                    .path("binModel", "price", "value")
                    .asPriceReading()
                    ?.listing,
            extendedBidding = isAuction && extendedBidding(sources),
            userIsHighBidder = if (signedIn == false) null else sources.semantic?.get("isHighBidder").asBoolean(),
            userBiddingStatus = if (signedIn == false) null else userBiddingStatus(sources),
            endedText = endedText,
            endedEarly = status == ListingStatus.ENDED && endedEarly(endTime, scheduledEnd, endedText),
            signedIn = signedIn,
            relistedItemId = relistedItemId(sources, itemId),
            fetchedAtUtcMillis = fetchedAtUtcMillis,
        )
    }

    private fun isAuction(sources: ItemPageSources): Boolean {
        val semantic = sources.semantic
        return semantic?.get("currentBidPrice").asObject() != null ||
            semantic?.get("bidCount").asWholeNumber() != null ||
            semantic?.get("auctionPossible").asBoolean() == true ||
            sources.buyBox?.get("bidPrice").asObject() != null ||
            sources.buyBox.path("bidInfo", "bidCount") != null
    }

    private fun currentPrice(
        sources: ItemPageSources,
        isAuction: Boolean,
    ): PriceReading? =
        if (isAuction) {
            sources.semantic?.get("currentBidPrice").asPriceReading()
                ?: sources.buyBox.path("bidPrice", "value").asPriceReading()
        } else {
            // JSON-LD offers.price is the Buy It Now price, which is the price of a fixed-price listing.
            sources.buyBox.path("binModel", "price", "value").asPriceReading()
                ?: sources.semantic?.get("currentBidPrice").asPriceReading()
                ?: jsonLdOfferPrice(sources.product)
        }

    private fun jsonLdOfferPrice(product: JsonObject?): PriceReading? {
        val offer = offers(product).firstOrNull() ?: return null
        val value = offer["price"].asDecimal() ?: return null
        val currency = offer["priceCurrency"].asCurrencyCode() ?: return null
        return moneyOrNull(value, currency)?.let { PriceReading(it, null) }
    }

    private fun endTime(sources: ItemPageSources): Long? =
        parseUtcMillis(sources.semantic.path("endDate", "value").asText())
            ?: parseUtcMillis(sources.buyBox.path("bidInfo", "endTime", "endTime", "value").asText())
            ?: sources.listingLifecycle.firstNotNullOfOrNull { parseUtcMillis(it["endDate"].asText()) }
            ?: sources.auctionData.firstNotNullOfOrNull { parseUtcMillis(it["biddingEndedAt"].asText()) }
            ?: sources.auctionData.firstNotNullOfOrNull { parseUtcMillis(it["endAtOrAfter"].asText()) }

    /** listingStatus when eBay states it; otherwise biddingEndedAt or an ended banner mean ENDED. */
    private fun status(sources: ItemPageSources): ListingStatus {
        when (
            sources.semantic
                ?.get("listingStatus")
                .asText()
                ?.trim()
                ?.uppercase()
        ) {
            "ACTIVE" -> return ListingStatus.ACTIVE
            in ENDED_STATUSES -> return ListingStatus.ENDED
        }
        if (sources.auctionData.any {
                parseUtcMillis(
                    it["biddingEndedAt"].asText(),
                ) != null
            }
        ) {
            return ListingStatus.ENDED
        }
        val banner = statusMessageText(sources)
        if (banner != null && ENDED_BANNER.containsMatchIn(banner)) return ListingStatus.ENDED
        return ListingStatus.ACTIVE
    }

    private fun statusMessageText(sources: ItemPageSources): String? {
        val module = sources.statusMessage ?: return null
        val section = module.path("sections", "statusMessage") ?: module
        return HtmlText.cleanText(section.collectTexts().joinToString("")).takeIf { it.isNotEmpty() }
    }

    private fun title(sources: ItemPageSources): String? =
        sequenceOf<() -> String?>(
            { sources.product?.get("name").asText() },
            { HtmlText.metaContent(sources.pageText, "og:title")?.let(::withoutSiteSuffix) },
            { HtmlText.documentTitle(sources.pageText)?.let(::withoutSiteSuffix) },
            { PageModelExtractor.module(sources.pageText, "TITLE", "TitleViewModel").collectTexts().firstOrNull() },
        ).mapNotNull { source -> source()?.let(HtmlText::cleanText) }
            .firstOrNull { it.isNotEmpty() }

    /** "Britain after Rome ... | eBay UK" -> "Britain after Rome ...". */
    private fun withoutSiteSuffix(title: String): String = title.replace(SITE_SUFFIX, "")

    private fun imageUrl(sources: ItemPageSources): String? {
        val image = sources.product?.get("image")
        val fromJsonLd =
            image.asText()
                ?: image.asArray()?.firstNotNullOfOrNull { it.asText() ?: it.asObject()?.get("url").asText() }
                ?: image.asObject()?.get("url").asText()
        return listOfNotNull(fromJsonLd, HtmlText.metaContent(sources.pageText, "og:image"))
            .map { it.trim() }
            .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun bidCount(sources: ItemPageSources): Int {
        val count =
            sources.semantic?.get("bidCount").asWholeNumber()
                ?: sources.buyBox.path("bidInfo", "bidCount", "value").asWholeNumber()
                ?: 0
        return count.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    /** False when eBay says the reserve is not met, true when it says met, null when it says nothing. */
    private fun reserveMet(buyBoxText: String): Boolean? =
        when {
            RESERVE_NOT_MET.containsMatchIn(buyBoxText) -> false
            RESERVE_MET.containsMatchIn(buyBoxText) -> true
            else -> null
        }

    /**
     * Postage to the site's own country, in the listing's currency when eBay shows it. JSON-LD lists one rate per
     * destination (the viewer's and sometimes the site's country); for a listing from another site its rate can be
     * a conversion, in which case the postage summary's own figure ("US $10.00 (approx £7.43)") is preferred.
     */
    private fun postage(
        sources: ItemPageSources,
        site: EbaySite,
        listingCurrency: String,
    ): Money? {
        val fromJsonLd = jsonLdPostage(sources.product, site)
        if (fromJsonLd != null && fromJsonLd.currency == listingCurrency) return fromJsonLd
        val fromSummary = summaryPostage(sources.shippingSummary, listingCurrency)
        return fromSummary ?: fromJsonLd
    }

    private fun jsonLdPostage(
        product: JsonObject?,
        site: EbaySite,
    ): Money? {
        // shippingDetails is an array in 2026 pages and a single object in 2025 ones.
        val details = offers(product).flatMap { offer -> offer["shippingDetails"].asList() }
        val rates =
            details.mapNotNull { detail ->
                val value = detail.path("shippingRate", "value").asDecimal() ?: return@mapNotNull null
                val currency = detail.path("shippingRate", "currency").asCurrencyCode() ?: return@mapNotNull null
                val country =
                    detail
                        .path("shippingDestination", "addressCountry")
                        .asText()
                        ?.trim()
                        ?.uppercase()
                moneyOrNull(value, currency)?.let { country to it }
            }
        val homeCountries = EbayDomains.countryCodes(site)
        return (rates.firstOrNull { (country, _) -> country in homeCountries } ?: rates.firstOrNull())?.second
    }

    /** The figure after the "Postage:" / "Shipping:" label in eBay's postage summary; "Free" is zero. */
    private fun summaryPostage(
        summary: JsonObject?,
        listingCurrency: String,
    ): Money? {
        val labelled =
            summary.firstObject { candidate ->
                val label = candidate["labels"].collectTexts().joinToString(" ").trim()
                candidate["values"] != null && POSTAGE_LABEL.matches(label)
            } ?: return null
        val firstValue =
            labelled["values"]
                .asArray()
                ?.firstOrNull()
                .collectTexts()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?: return null
        if (FREE_POSTAGE.matches(firstValue)) return Money.zero(listingCurrency)
        return DisplayMoney.firstIn(firstValue, listingCurrency)
    }

    /**
     * The fee eBay says is inside the displayed price ("incl. £0.94 for Buyer Protection"). Auctions carry it only
     * as text in bidPriceProgram; fixed-price listings also have binModel.buyerProtectionFee. A fee in another
     * currency than the price cannot be part of it and is ignored.
     */
    private fun buyerProtectionFee(
        sources: ItemPageSources,
        isAuction: Boolean,
        price: Money,
    ): Money? {
        val structured =
            if (isAuction) {
                null
            } else {
                sources.buyBox
                    .path("binModel", "buyerProtectionFee")
                    .asPriceReading()
                    ?.listing
            }
        val program = sources.buyBox?.get("bidPriceProgram")
        val fromText =
            listOf(program.collectTexts().joinToString(""), sources.buyBox.collectTexts().joinToString(" "))
                .firstNotNullOfOrNull { text ->
                    BUYER_PROTECTION.find(text)?.let { DisplayMoney.firstIn(it.groupValues[1], price.currency) }
                }
        return (structured ?: fromText)?.takeIf { it.currency == price.currency && it < price }
    }

    /**
     * Non-null auctionData.extendedBidding, or the listing's own label ("Extended bidding", "Bids placed in the last
     * 1 minute reset the time to 60 seconds."). Every page also ships the label as an i18n template with {minutes}
     * and {extension} placeholders (and, in 2025 bundles, "reset the time left to 2 minutes"), so only rendered
     * forms count: a TextSpan or DOM text node "Extended bidding", or "reset the time to" followed by a number.
     */
    private fun extendedBidding(sources: ItemPageSources): Boolean {
        val flagged =
            sources.auctionData.any { data ->
                val value = data["extendedBidding"]
                value != null && !value.isJsonNull() && value.asBoolean() != false
            }
        return flagged || EXTENDED_LABEL_PATTERNS.any { it.containsMatchIn(sources.pageText) }
    }

    private fun userBiddingStatus(sources: ItemPageSources): String? =
        sources.auctionData.firstNotNullOfOrNull { data ->
            data
                .path("bidderAuctionDetails", "userBiddingStatus")
                .asText()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

    private fun endedEarly(
        endTime: Long,
        scheduledEnd: Long?,
        endedText: String?,
    ): Boolean =
        (scheduledEnd != null && endTime < scheduledEnd - EARLY_END_TOLERANCE_MILLIS) ||
            (endedText != null && ENDED_BY_SELLER.containsMatchIn(endedText))

    /** GHpre.userAuth from the global header, else the model's signedIn flag; null when the page says neither. */
    private fun signedIn(pageText: String): Boolean? =
        USER_AUTH
            .find(pageText)
            ?.groupValues
            ?.get(1)
            ?.toBooleanStrictOrNull()
            ?: SIGNED_IN
                .find(pageText)
                ?.groupValues
                ?.get(1)
                ?.toBooleanStrictOrNull()

    /** relistedId when eBay names one, else the page's own item number when eBay redirected to another listing. */
    private fun relistedItemId(
        sources: ItemPageSources,
        requestedItemId: String,
    ): String? {
        val relisted =
            sources.semantic
                ?.get("relistedId")
                .asText()
                ?.trim()
                ?.takeIf { ITEM_NUMBER.matches(it) }
        return (relisted ?: sources.pageItemId)?.takeIf { it != requestedItemId }
    }

    private fun offers(product: JsonObject?): List<JsonObject> =
        product?.get("offers").asList().mapNotNull {
            it.asObject()
        }

    private val ENDED_STATUSES = setOf("ENDED", "COMPLETED", "CLOSED", "SOLD", "UNSOLD")
    private val ITEM_NUMBER = Regex("\\d{9,15}")
    private val SITE_SUFFIX = Regex("\\s*\\|\\s*eBay(?:\\.[a-z.]+)?(?:\\s+[A-Za-z]{2,3})?\\s*$")

    private val ENDED_BANNER =
        Regex(
            "item sold on|bidding ended on|this listing (?:was ended|has ended|ended)|was ended by the seller|" +
                "verkauft am|angebot (?:wurde )?beendet|vom verkäufer beendet",
            RegexOption.IGNORE_CASE,
        )
    private val ENDED_BY_SELLER =
        Regex("was ended by the seller|vom verkäufer (?:vorzeitig )?beendet", RegexOption.IGNORE_CASE)
    private val RESERVE_NOT_MET =
        Regex("reserve (?:price )?not met|mindestpreis (?:wurde )?noch nicht erreicht", RegexOption.IGNORE_CASE)
    private val RESERVE_MET = Regex("reserve (?:price )?met|mindestpreis (?:wurde )?erreicht", RegexOption.IGNORE_CASE)

    // "incl. £0.94 for Buyer Protection" (2025-2026) and "incl. £2.70 Buyer Protection fee" (2026-09).
    private val BUYER_PROTECTION =
        Regex("(?:including|includes?|incl\\.?)\\s*(.{1,24}?)\\s*(?:for\\s+)?Buyer Protection", RegexOption.IGNORE_CASE)
    private val POSTAGE_LABEL = Regex("(?:postage|shipping|versand)\\s*:?", RegexOption.IGNORE_CASE)
    private val FREE_POSTAGE =
        Regex("(?:free|kostenlos|gratis)(?:\\s+(?:postage|shipping|delivery|versand))?\\.?", RegexOption.IGNORE_CASE)
    private val EXTENDED_LABEL_PATTERNS =
        listOf(
            Regex("reset the time to \\d", RegexOption.IGNORE_CASE),
            Regex("\"text\"\\s*:\\s*\"\\s*Extended bidding", RegexOption.IGNORE_CASE),
            Regex(">\\s*Extended bidding\\s*<", RegexOption.IGNORE_CASE),
        )
    private val USER_AUTH = Regex("\"userAuth\"\\s*:\\s*(true|false)")
    private val SIGNED_IN = Regex("\"signedIn\"\\s*:\\s*(true|false)")

    // eBay stores end times to the second; a minute of tolerance keeps rounding from ever looking like an early end.
    private const val EARLY_END_TOLERANCE_MILLIS = 60_000L
}
