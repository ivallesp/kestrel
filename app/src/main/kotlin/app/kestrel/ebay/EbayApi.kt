package app.kestrel.ebay

import app.kestrel.model.AutoRefreshState
import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import app.kestrel.model.ParsedLink
import java.util.Locale

// Public API of the eBay knowledge layer. Owned by the ebay-core lane; bodies are stubs until it lands.

object ItemUrlParser {
    /** Finds an eBay listing in shared or pasted text (a URL, share text containing a URL, or a bare item number). */
    fun parse(
        text: String,
        defaultSite: EbaySite = EbaySite.UK,
    ): ParsedLink = TODO("ebay-core lane")
}

object ListingParser {
    /**
     * Parses an item page. [pageText] is either the full HTML or the reduced page produced by kestrel.js
     * `modelText()` (title, meta tags and the inline scripts that carry the page model and JSON-LD).
     * Returns null when the text carries no item model.
     */
    fun parse(
        pageText: String,
        requested: ItemRef,
        fetchedAtUtcMillis: Long,
    ): Listing? = TODO("ebay-core lane")
}

object AutoRefreshParser {
    /** Parses the JSON reply of the page's AUTO_REFRESH (/itemmodules/<id>?module_groups=AUTO_REFRESH...) call. */
    fun parse(json: String): AutoRefreshState? = TODO("ebay-core lane")

    /** The AUTO_REFRESH URL path from the page model (AUTOREFRESH.autoRefreshAction.URL), if present. */
    fun urlFromPage(pageText: String): String? = TODO("ebay-core lane")
}

object PageClassifier {
    fun classify(
        url: String,
        title: String,
    ): PageKind = TODO("ebay-core lane")
}

object BidIncrements {
    /** eBay's published increment for a price on a site (fallback when the live minimum cannot be read). */
    fun increment(
        site: EbaySite,
        price: Money,
    ): Money = TODO("ebay-core lane")

    /**
     * The lowest bid eBay will accept: the current price when there are no bids, else current plus one increment.
     * For UK private-seller listings (fee included in displayed prices) returns the larger of the two documented
     * ways of computing it, so Kestrel never treats a too-low bid as valid.
     */
    fun minimumNextBid(
        site: EbaySite,
        current: Money,
        bidCount: Int,
        buyerProtectionIncluded: Boolean,
    ): Money = TODO("ebay-core lane")
}

object BuyerProtectionFee {
    /** ebay.co.uk Buyer Protection fee for an item price from a UK private seller (since 2025-07-17). */
    fun feeFor(itemPrice: Money): Money = TODO("ebay-core lane")

    /** The item price inside a fee-inclusive displayed price. */
    fun itemPriceFromDisplayed(displayed: Money): Money = TODO("ebay-core lane")
}

object MoneyText {
    /** "£1,234.56", "US$69.99", "€7.50" for display in the app's locale. */
    fun format(
        money: Money,
        displayLocale: Locale = Locale.UK,
    ): String = TODO("ebay-core lane")

    /** Parses what the user typed ("90", "90.37", "£90.37", "1,234.50", "90,37" for EUR) in [currency]. */
    fun parseUserInput(
        text: String,
        currency: String,
    ): Money? = TODO("ebay-core lane")
}
