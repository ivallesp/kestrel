package app.kestrel.ebay

import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.ParsedLink

/** Finds the eBay listing in shared or pasted text. Implements [ItemUrlParser]. */
internal object ItemLinkFinder {
    fun find(
        text: String,
        defaultSite: EbaySite,
    ): ParsedLink {
        val boundedText = text.take(MAXIMUM_TEXT_LENGTH)
        var shortLink: ParsedLink.ShortLink? = null
        var notAListing: ParsedLink.NotAListing? = null
        for (candidate in urlCandidates(boundedText)) {
            when (val link = classify(candidate, allowNested = true)) {
                is ParsedLink.Item -> return link
                is ParsedLink.ShortLink -> if (shortLink == null) shortLink = link
                is ParsedLink.NotAListing -> if (notAListing == null) notAListing = link
                ParsedLink.NotEbay -> Unit
            }
        }
        shortLink?.let { return it }
        notAListing?.let { return it }
        BARE_ITEM_NUMBER.matchEntire(boundedText.trim())?.let {
            return ParsedLink.Item(ItemRef(defaultSite, it.groupValues[1]))
        }
        return ParsedLink.NotEbay
    }

    /** One http(s) URL, as a [ParsedLink]. */
    fun classify(
        url: String,
        allowNested: Boolean,
    ): ParsedLink {
        val parts = UrlParts.parse(url) ?: return ParsedLink.NotEbay
        if (EbayDomains.isShortLinkHost(parts.host)) {
            return if (SHORT_LINK_PATH.matches(parts.path)) {
                ParsedLink.ShortLink("https://${parts.host}${parts.path.trimEnd('/')}")
            } else {
                ParsedLink.NotAListing(withoutTracking(parts))
            }
        }
        val site = EbayDomains.siteForHost(parts.host)
        if (site == null) {
            // Link wrappers (Facebook, Google, mail redirectors) carry the eBay URL as a parameter.
            return if (allowNested) nestedEbayLink(parts) ?: ParsedLink.NotEbay else ParsedLink.NotEbay
        }
        ITEM_PATH.find(parts.path)?.let { return ParsedLink.Item(ItemRef(site, it.groupValues[1])) }
        val isNonListingPage = NON_LISTING_PATHS.any { parts.path.startsWith(it, ignoreCase = true) }
        if (!isNonListingPage) {
            ITEM_PARAMETERS
                .firstNotNullOfOrNull { name ->
                    parts.parameter(name)?.trim()?.takeIf { ITEM_NUMBER.matches(it) }
                }?.let { return ParsedLink.Item(ItemRef(site, it)) }
            // Sign-in returns (ru=), affiliate redirects (mpre=) and similar carry the listing URL inside.
            if (allowNested) nestedEbayLink(parts)?.let { if (it !is ParsedLink.NotAListing) return it }
        }
        return ParsedLink.NotAListing(withoutTracking(parts))
    }

    /** The URL without the tracking parameters eBay and its share sheets add (bidflow UR5). */
    fun withoutTracking(parts: UrlParts): String =
        parts.rebuilt { parameter ->
            parameter.name.lowercase() in TRACKING_PARAMETERS ||
                parameter.name.startsWith("utm_", ignoreCase = true) ||
                (parameter.name.equals("var", ignoreCase = true) && parameter.value.isNullOrBlank())
        }

    private fun nestedEbayLink(parts: UrlParts): ParsedLink? =
        parts.query
            .asSequence()
            .mapNotNull { it.value?.trim() }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .map { classify(it, allowNested = false) }
            .firstOrNull { it != ParsedLink.NotEbay }

    /** URLs in free text: http(s) URLs, then scheme-less eBay addresses such as "ebay.us/m/100axl". */
    private fun urlCandidates(text: String): List<String> {
        val candidates = mutableListOf<String>()
        val remainder = StringBuilder(text)
        for (match in HTTP_URL.findAll(text)) {
            candidates += trimTrailingPunctuation(match.value)
            for (index in match.range) remainder.setCharAt(index, ' ')
        }
        for (match in SCHEMELESS_EBAY_URL.findAll(remainder)) {
            candidates +=
                "https://" + trimTrailingPunctuation(match.value)
        }
        return candidates.filter { it.length <= MAXIMUM_URL_LENGTH }
    }

    private fun trimTrailingPunctuation(url: String): String =
        url.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"', '…', '>')

    private val ITEM_NUMBER = Regex("\\d{9,15}")
    private val ITEM_PATH = Regex("^/itm/(?:[^/]+/)?(\\d{9,15})(?:/|$)", RegexOption.IGNORE_CASE)
    private val ITEM_PARAMETERS = listOf("item", "itemId", "iid")
    private val SHORT_LINK_PATH = Regex("^/(?:m/)?[A-Za-z0-9]{4,12}/?$")

    // Search, store, catalogue, browse and member pages. A catalogue page's iid= names a listing the page was
    // reached from, not the page itself, so these never become listings.
    private val NON_LISTING_PATHS = listOf("/sch/", "/str/", "/p/", "/b/", "/usr/")

    private val BARE_ITEM_NUMBER =
        Regex("(?i)(?:(?:ebay\\s+)?item(?:\\s+(?:number|no\\.?|#))?\\s*[:#]?\\s*)?#?(\\d{9,15})")

    private val HTTP_URL = Regex("(?i)https?://[^\\s<>\"'`]+")
    private val SCHEMELESS_EBAY_URL =
        Regex(
            "(?i)(?<![\\w.@/-])(?:[a-z0-9-]+\\.)*ebay\\." +
                "(?:us|io|co\\.uk|com\\.au|com|de|at|ch|fr|it|es|nl|ie|ca|pl)/[^\\s<>\"'`]*",
        )

    // bidflow UR5 as corrected by its adversarial check, which keeps "rt".
    private val TRACKING_PARAMETERS =
        setOf(
            "_skw",
            "itmmeta",
            "hash",
            "itmprp",
            "_trksid",
            "mkcid",
            "mkevt",
            "mkrid",
            "ssspo",
            "sssrc",
            "ssuid",
            "stype",
            "widget_ver",
            "media",
            "sojtags",
            "roken",
            "campid",
            "customid",
            "toolid",
            "siteid",
            "_mwbanner",
            "nordt",
            "epid",
            "ul_ref",
            "_rdt",
            "ul_noapp",
        )

    private const val MAXIMUM_URL_LENGTH = 8_192
    private const val MAXIMUM_TEXT_LENGTH = 100_000
}
