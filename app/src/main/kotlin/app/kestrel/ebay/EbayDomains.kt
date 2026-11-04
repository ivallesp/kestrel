package app.kestrel.ebay

import app.kestrel.model.EbaySite

/** Which hosts belong to eBay and to which site. */
internal object EbayDomains {
    /** The site whose domain is [host] or a subdomain of it (www., m., cgi., signin., rover., ...). */
    fun siteForHost(host: String): EbaySite? {
        val normalized = normalizeHost(host) ?: return null
        return EbaySite.entries.firstOrNull { site ->
            val domain = site.host.removePrefix("www.")
            normalized == domain || normalized.endsWith(".$domain")
        }
    }

    /** ebay.us and ebay.io serve share links that redirect to an item on the sharer's site. */
    fun isShortLinkHost(host: String): Boolean = normalizeHost(host) in SHORT_LINK_HOSTS

    fun isEbayHost(host: String): Boolean = siteForHost(host) != null || isShortLinkHost(host)

    /** Lower case, without a trailing dot; null when it cannot be a host name. */
    fun normalizeHost(host: String): String? {
        val normalized = host.trim().trimEnd('.').lowercase()
        return normalized.takeIf { it.isNotEmpty() && HOST_NAME.matches(it) }
    }

    /** The country a site serves, as ISO 3166 alpha-3 and alpha-2 codes (JSON-LD shipping destinations use both). */
    fun countryCodes(site: EbaySite): Set<String> =
        when (site) {
            EbaySite.UK -> setOf("GBR", "GB", "UK")
            EbaySite.US -> setOf("USA", "US")
            EbaySite.DE -> setOf("DEU", "DE")
            EbaySite.AT -> setOf("AUT", "AT")
            EbaySite.CH -> setOf("CHE", "CH")
            EbaySite.FR -> setOf("FRA", "FR")
            EbaySite.IT -> setOf("ITA", "IT")
            EbaySite.ES -> setOf("ESP", "ES")
            EbaySite.NL -> setOf("NLD", "NL")
            EbaySite.IE -> setOf("IRL", "IE")
            EbaySite.CA -> setOf("CAN", "CA")
            EbaySite.AU -> setOf("AUS", "AU")
            EbaySite.PL -> setOf("POL", "PL")
        }

    private val SHORT_LINK_HOSTS = setOf("ebay.us", "www.ebay.us", "ebay.io", "www.ebay.io")
    private val HOST_NAME = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*")
}
