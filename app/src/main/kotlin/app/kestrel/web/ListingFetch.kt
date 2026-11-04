package app.kestrel.web

import app.kestrel.model.ItemRef
import app.kestrel.model.PageKind
import app.kestrel.model.ParsedLink

/**
 * Loads a listing for the add-snipe preview in a fresh headless page. Short links are resolved by letting the
 * WebView follow eBay's redirect and reading the item from the page it lands on.
 */
internal class ListingFetch(
    private val newDriver: suspend () -> PageDriver,
    private val script: PageScript,
    private val parsers: EbayParsers,
    private val utcClock: () -> Long,
    private val timing: PageTiming = PageTiming(),
) {
    suspend fun fetch(link: ParsedLink): FetchResult =
        when (link) {
            is ParsedLink.Item -> {
                open(link.ref.url) { view, reader -> read(reader, link.ref, view) }
            }

            is ParsedLink.ShortLink -> {
                open(link.url) { view, reader ->
                    when (val target = parsers.parseLink(view.url)) {
                        is ParsedLink.Item -> read(reader, target.ref, view)
                        is ParsedLink.NotAListing -> FetchResult.NotAnAuction(null)
                        else -> FetchResult.Failed("The link did not lead to an eBay listing (${view.url})")
                    }
                }
            }

            is ParsedLink.NotAListing -> {
                FetchResult.NotAnAuction(null)
            }

            ParsedLink.NotEbay -> {
                FetchResult.Failed("This is not an eBay link")
            }
        }

    private suspend fun open(
        url: String,
        onItemPage: suspend (PageView, ItemPageReader) -> FetchResult,
    ): FetchResult {
        val driver = newDriver()
        try {
            val reader =
                ItemPageReader(
                    driver,
                    PageHelper(driver, script, timing.scriptTimeoutMillis),
                    parsers,
                    utcClock,
                    timing,
                )
            val view = reader.settle(driver.load(url, timing.loadTimeoutMillis))
            return when (view.kind) {
                PageKind.NORMAL -> onItemPage(view, reader)
                PageKind.NOT_FOUND -> FetchResult.Failed("eBay has no listing at this link")
                else -> FetchResult.Blocked(view.kind, view.url)
            }
        } catch (problem: WebPageException) {
            return FetchResult.Failed(problem.message ?: "The page could not be loaded")
        } finally {
            driver.destroy()
        }
    }

    private suspend fun read(
        reader: ItemPageReader,
        ref: ItemRef,
        view: PageView,
    ): FetchResult {
        val read = reader.listing(ref)
        val listing =
            read.listing
                ?: return FetchResult.Failed("Kestrel could not read the listing at ${view.url} (${read.problem})")
        return if (listing.isAuction) FetchResult.Loaded(listing) else FetchResult.NotAnAuction(listing)
    }
}
