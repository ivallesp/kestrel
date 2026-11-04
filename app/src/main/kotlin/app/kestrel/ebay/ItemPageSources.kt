package app.kestrel.ebay

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * The parts of one item page that [ListingReader] reads, located once. Every source is optional: eBay drops
 * modules between builds and between listing types (fixed price pages have no auction fields).
 */
internal class ItemPageSources(
    val pageText: String,
) {
    val semantic: JsonObject? = PageModelExtractor.module(pageText, "SEMANTIC_DATA", "SemanticDataModule")
    val buyBox: JsonObject? = PageModelExtractor.module(pageText, "BUY_BOX", "BuyBoxModule")
    val statusMessage: JsonObject? = PageModelExtractor.module(pageText, "STATUS_MESSAGE", "LayoutSectionModule")
    val shippingSummary: JsonObject? =
        PageModelExtractor.module(pageText, "SHIPPING_ATF_SECTION_MODULE", "LayoutSectionModule")
            ?: PageModelExtractor.module(pageText, "SHIPPING_SECTION_MODULE", "LayoutSectionModule")
    val product: JsonObject? = jsonLdProduct(pageText)
    val canonicalUrl: String? = HtmlText.canonicalHref(pageText)
    val openGraphUrl: String? = HtmlText.metaContent(pageText, "og:url")

    /** The item number the page itself describes; differs from the requested one after a redirect. */
    val pageItemId: String? =
        semantic
            ?.get("listingId")
            .asText()
            ?.trim()
            ?.takeIf { ITEM_NUMBER.matches(it) }
            ?: listOfNotNull(canonicalUrl, openGraphUrl).firstNotNullOfOrNull(::itemIdInUrl)

    /**
     * auctionData objects that belong to this listing: the SEMANTIC_DATA copy and the GraphQL copy
     * (data.listingV2ById, later data.pixListingById). Found by key text so another rename of the GraphQL root does
     * not hide them; a copy whose nearest preceding item number is another listing's is skipped, so recommendation
     * carousels never leak into this listing.
     */
    val auctionData: List<JsonObject> = ownObjectsAfterKey("auctionData")

    /** listingLifecycle objects of this listing (GraphQL copy; carries endDate as a plain ISO string). */
    val listingLifecycle: List<JsonObject> = ownObjectsAfterKey("listingLifecycle")

    private fun ownObjectsAfterKey(key: String): List<JsonObject> =
        PageModelExtractor.occurrencesOfKey(pageText, key).mapNotNull { (keyAt, objectText) ->
            val nearestItemId = nearestItemIdBefore(keyAt)
            if (nearestItemId != null && pageItemId != null && nearestItemId != pageItemId) return@mapNotNull null
            parseJsonOrNull(objectText).asObject()
        }

    private fun nearestItemIdBefore(position: Int): String? {
        val windowStart = (position - OWNERSHIP_WINDOW).coerceAtLeast(0)
        val window = pageText.substring(windowStart, position)
        return ITEM_ID_FIELD
            .findAll(window)
            .lastOrNull()
            ?.groupValues
            ?.get(1)
    }

    private companion object {
        val ITEM_NUMBER = Regex("\\d{9,15}")
        val ITEM_ID_FIELD = Regex("\"(?:itemId|listingId)\"\\s*:\\s*\"(\\d{9,15})\"")
        val ITEM_URL_ID = Regex("/itm/(?:[^/?#]+/)?(\\d{9,15})(?:[/?#]|$)")

        // SEMANTIC_DATA's listingId sits about 400 characters before its auctionData; a recommendation card keeps its
        // own itemId much closer than that.
        const val OWNERSHIP_WINDOW = 1_500

        fun itemIdInUrl(url: String): String? = ITEM_URL_ID.find(url)?.groupValues?.get(1)

        /** The JSON-LD object of @type Product, also when wrapped in an array or an @graph. */
        fun jsonLdProduct(pageText: String): JsonObject? =
            HtmlText.scriptBodies(pageText, "ld+json").firstNotNullOfOrNull { body ->
                parseJsonOrNull(body.trim()).firstObject { candidate -> isProduct(candidate) }
            }

        private fun isProduct(candidate: JsonObject): Boolean {
            val type = candidate["@type"]
            return type.asText() == "Product" || (type as? JsonArray)?.any { it.asText() == "Product" } == true
        }
    }
}
