package app.kestrel.web

import app.kestrel.ebay.AutoRefreshParser
import app.kestrel.ebay.ItemUrlParser
import app.kestrel.ebay.ListingParser
import app.kestrel.ebay.PageClassifier
import app.kestrel.model.AutoRefreshState
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.PageKind
import app.kestrel.model.ParsedLink

/** The eBay knowledge the WebView layer uses, behind an interface so its tests can supply their own. */
internal interface EbayParsers {
    fun classify(
        url: String,
        title: String,
    ): PageKind

    fun parseListing(
        pageText: String,
        requested: ItemRef,
        fetchedAtUtcMillis: Long,
    ): Listing?

    fun parseAutoRefresh(json: String): AutoRefreshState?

    fun autoRefreshUrl(pageText: String): String?

    fun parseLink(text: String): ParsedLink

    /** The ebay package's parsers. */
    object Default : EbayParsers {
        override fun classify(
            url: String,
            title: String,
        ): PageKind = PageClassifier.classify(url, title)

        override fun parseListing(
            pageText: String,
            requested: ItemRef,
            fetchedAtUtcMillis: Long,
        ): Listing? = ListingParser.parse(pageText, requested, fetchedAtUtcMillis)

        override fun parseAutoRefresh(json: String): AutoRefreshState? = AutoRefreshParser.parse(json)

        override fun autoRefreshUrl(pageText: String): String? = AutoRefreshParser.urlFromPage(pageText)

        override fun parseLink(text: String): ParsedLink = ItemUrlParser.parse(text)
    }
}
