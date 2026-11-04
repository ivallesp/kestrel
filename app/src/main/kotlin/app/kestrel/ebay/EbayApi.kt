package app.kestrel.ebay

import app.kestrel.model.AutoRefreshState
import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import app.kestrel.model.ParsedLink
import java.util.Locale

// Public API of the eBay knowledge layer. Owned by the ebay-core lane. Pure Kotlin: nothing here touches the
// network; every input is text the WebView already loaded or the user typed.

object ItemUrlParser {
    /** Finds an eBay listing in shared or pasted text (a URL, share text containing a URL, or a bare item number). */
    fun parse(
        text: String,
        defaultSite: EbaySite = EbaySite.UK,
    ): ParsedLink = readSafely(ParsedLink.NotEbay) { ItemLinkFinder.find(text, defaultSite) }
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
    ): Listing? = readSafely(null) { ListingReader.read(pageText, requested, fetchedAtUtcMillis) }
}

object AutoRefreshParser {
    /** Parses the JSON reply of the page's AUTO_REFRESH (/itemmodules/<id>?module_groups=AUTO_REFRESH...) call. */
    fun parse(json: String): AutoRefreshState? = readSafely(null) { AutoRefreshReader.read(json) }

    /** The AUTO_REFRESH URL path from the page model (AUTOREFRESH.autoRefreshAction.URL), if present. */
    fun urlFromPage(pageText: String): String? = readSafely(null) { AutoRefreshReader.urlFromPage(pageText) }
}

object PageClassifier {
    fun classify(
        url: String,
        title: String,
    ): PageKind = readSafely(PageKind.OTHER) { PageKinds.classify(url, title) }
}

object BidIncrements {
    /** eBay's published increment for a price on a site (fallback when the live minimum cannot be read). */
    fun increment(
        site: EbaySite,
        price: Money,
    ): Money = Money(IncrementTables.forPrice(site, price.currency).incrementMinor(price.minor), price.currency)

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
    ): Money {
        val price = if (current.minor < 0) Money.zero(current.currency) else current
        if (bidCount <= 0) return price
        val onDisplayedPrice = price + increment(site, price)
        if (!buyerProtectionIncluded || price.currency != BuyerProtectionFormula.CURRENCY) return onDisplayedPrice
        // rules.md INC6 and its open question: one reading applies the increment to the price without the fee and
        // adds the fee back (£191.83 + £5 + fee = £205.40), the other adds the increment to the fee-inclusive price
        // (£200.20 + £5 = £205.20). Neither is confirmed, so the higher one is used.
        val itemPrice = BuyerProtectionFee.itemPriceFromDisplayed(price)
        val nextItemPrice = itemPrice + increment(site, itemPrice)
        val withFeeOnTop = nextItemPrice + BuyerProtectionFee.feeFor(nextItemPrice)
        return maxOf(onDisplayedPrice, withFeeOnTop)
    }
}

object BuyerProtectionFee {
    /** ebay.co.uk Buyer Protection fee for an item price from a UK private seller (since 2025-07-17). */
    fun feeFor(itemPrice: Money): Money =
        if (itemPrice.currency == BuyerProtectionFormula.CURRENCY) {
            Money(BuyerProtectionFormula.feeMinor(itemPrice.minor), itemPrice.currency)
        } else {
            // The fee is charged on ebay.co.uk in pounds only; other currencies carry no UK fee.
            Money.zero(itemPrice.currency)
        }

    /** The item price inside a fee-inclusive displayed price. */
    fun itemPriceFromDisplayed(displayed: Money): Money =
        if (displayed.currency == BuyerProtectionFormula.CURRENCY) {
            Money(BuyerProtectionFormula.itemPriceMinor(displayed.minor), displayed.currency)
        } else {
            displayed
        }
}

object MoneyText {
    /** "£1,234.56", "US$69.99", "€7.50" for display in the app's locale. */
    fun format(
        money: Money,
        displayLocale: Locale = Locale.UK,
    ): String = MoneyFormatting.format(money, displayLocale)

    /** Parses what the user typed ("90", "90.37", "£90.37", "1,234.50", "90,37" for EUR) in [currency]. */
    fun parseUserInput(
        text: String,
        currency: String,
    ): Money? = readSafely(null) { MoneyFormatting.parseUserInput(text, currency) }
}

/**
 * Runs a reader and turns an unexpected failure into [fallback] (no listing, not eBay, a page that needs the user).
 * Pages come from eBay and text from other apps; whatever they hold must never crash a snipe window.
 */
private inline fun <T> readSafely(
    fallback: T,
    read: () -> T,
): T =
    try {
        read()
    } catch (_: RuntimeException) {
        fallback
    } catch (_: StackOverflowError) {
        fallback
    }
