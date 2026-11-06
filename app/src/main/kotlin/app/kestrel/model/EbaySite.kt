package app.kestrel.model

/**
 * An eBay marketplace. Kestrel signs in to one site, [HOME], and opens every listing there, whichever site its link
 * came from: eBay item numbers are global, and eBay shows a listing from another site in the listing's own currency
 * with an estimate in the home currency. The bid is made in the listing's currency, as eBay requires.
 */
enum class EbaySite(
    val host: String,
    val marketplaceId: String,
    val currency: String,
    val languageTag: String,
    val zoneId: String,
) {
    UK("www.ebay.co.uk", "EBAY_GB", "GBP", "en-GB", "Europe/London"),
    US("www.ebay.com", "EBAY_US", "USD", "en-US", "America/Los_Angeles"),
    DE("www.ebay.de", "EBAY_DE", "EUR", "de-DE", "Europe/Berlin"),
    AT("www.ebay.at", "EBAY_AT", "EUR", "de-AT", "Europe/Vienna"),
    CH("www.ebay.ch", "EBAY_CH", "CHF", "de-CH", "Europe/Zurich"),
    FR("www.ebay.fr", "EBAY_FR", "EUR", "fr-FR", "Europe/Paris"),
    IT("www.ebay.it", "EBAY_IT", "EUR", "it-IT", "Europe/Rome"),
    ES("www.ebay.es", "EBAY_ES", "EUR", "es-ES", "Europe/Madrid"),
    NL("www.ebay.nl", "EBAY_NL", "EUR", "nl-NL", "Europe/Amsterdam"),
    IE("www.ebay.ie", "EBAY_IE", "EUR", "en-IE", "Europe/Dublin"),
    CA("www.ebay.ca", "EBAY_CA", "CAD", "en-CA", "America/Toronto"),
    AU("www.ebay.com.au", "EBAY_AU", "AUD", "en-AU", "Australia/Sydney"),
    PL("www.ebay.pl", "EBAY_PL", "PLN", "pl-PL", "Europe/Warsaw"),
    ;

    val origin: String get() = "https://$host"

    fun itemUrl(itemId: String): String = "$origin/itm/$itemId"

    companion object {
        /** The site Kestrel signs in to and opens every listing on. */
        val HOME: EbaySite = UK

        /** Matches `ebay.co.uk`, `www.ebay.co.uk`, `m.ebay.co.uk` and similar host variants. */
        fun fromHost(host: String): EbaySite? {
            val bare =
                host
                    .lowercase()
                    .removePrefix("www.")
                    .removePrefix("m.")
                    .removePrefix("cgi.")
            return entries.firstOrNull { it.host.removePrefix("www.") == bare }
        }

        fun fromMarketplaceId(marketplaceId: String): EbaySite? =
            entries.firstOrNull {
                it.marketplaceId ==
                    marketplaceId
            }
    }
}

/** A listing on a specific site. [itemId] is eBay's legacy item number (digits), the same on every site. */
data class ItemRef(
    val site: EbaySite,
    val itemId: String,
) {
    val url: String get() = site.itemUrl(itemId)

    /** The same listing on the site Kestrel is signed in to. */
    fun onHomeSite(): ItemRef = if (site == EbaySite.HOME) this else copy(site = EbaySite.HOME)
}
