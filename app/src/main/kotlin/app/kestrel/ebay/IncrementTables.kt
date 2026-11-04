package app.kestrel.ebay

import app.kestrel.model.EbaySite

/**
 * eBay's published bid increments per site (docs/research/rules.md INC1-INC4). These are a fallback: eBay tests
 * other increments without notice (INC5), so the minimum shown in the bid layer always wins when it can be read.
 */
internal fun interface IncrementTable {
    fun incrementMinor(priceMinor: Long): Long
}

/** Rows of (lowest price of the band, increment), in minor units and ascending order. */
internal class BandedIncrements(
    private val bands: List<Pair<Long, Long>>,
) : IncrementTable {
    init {
        require(bands.isNotEmpty() && bands.zipWithNext().all { (lower, upper) -> lower.first < upper.first })
    }

    override fun incrementMinor(priceMinor: Long): Long =
        (
            bands.lastOrNull { (lowest, _) -> priceMinor >= lowest }
                ?: bands.first()
        ).second
}

internal object IncrementTables {
    /** ebay.co.uk, help page id=4014 (INC2). */
    val UNITED_KINGDOM =
        BandedIncrements(
            listOf(
                0L to 5L,
                100L to 20L,
                500L to 50L,
                1_500L to 100L,
                6_000L to 200L,
                15_000L to 500L,
                30_000L to 1_000L,
                60_000L to 2_000L,
                150_000L to 5_000L,
                300_000L to 10_000L,
            ),
        )

    /** ebay.com, identical on ebay.ca and ebay.com.au (INC1). */
    val UNITED_STATES =
        BandedIncrements(
            listOf(
                0L to 5L,
                100L to 25L,
                500L to 50L,
                2_500L to 100L,
                10_000L to 250L,
                25_000L to 500L,
                50_000L to 1_000L,
                100_000L to 2_500L,
                250_000L to 5_000L,
                500_000L to 10_000L,
            ),
        )

    /**
     * ebay.de, ebay.at and ebay.ch (INC3) and ebay.fr, ebay.it and ebay.es (INC4): the same coarse table. The German
     * table starts at 1,00 (the lowest start price there); below that its first row, 0,50, is used.
     */
    val EURO_COARSE =
        BandedIncrements(
            listOf(
                0L to 50L,
                5_000L to 100L,
                50_000L to 500L,
                100_000L to 1_000L,
                500_000L to 5_000L,
            ),
        )

    /** ebay.nl uses the UK table in euros (INC4). */
    val NETHERLANDS = UNITED_KINGDOM

    /** ebay.pl, US-style in złoty (INC4). It has no row below 1,00 zł, so its first row, 0,25, applies there. */
    val POLAND =
        BandedIncrements(
            listOf(
                0L to 25L,
                500L to 50L,
                2_500L to 100L,
                10_000L to 250L,
                25_000L to 500L,
                50_000L to 1_000L,
                100_000L to 2_500L,
                250_000L to 5_000L,
                500_000L to 10_000L,
            ),
        )

    /**
     * A euro listing seen on a non-euro site may come from any of the euro sites, whose tables differ a lot, so the
     * larger increment of the two euro tables is used: a computed minimum must never be too low.
     */
    val EURO_UNKNOWN_SITE =
        IncrementTable { price ->
            maxOf(EURO_COARSE.incrementMinor(price), NETHERLANDS.incrementMinor(price))
        }

    fun forSite(site: EbaySite): IncrementTable =
        when (site) {
            EbaySite.UK -> UNITED_KINGDOM

            EbaySite.US, EbaySite.CA, EbaySite.AU -> UNITED_STATES

            EbaySite.DE, EbaySite.AT, EbaySite.CH, EbaySite.FR, EbaySite.IT, EbaySite.ES -> EURO_COARSE

            EbaySite.NL -> NETHERLANDS

            // No table in the research for ebay.ie. It is an English-language site run alongside ebay.co.uk, and the
            // closest documented euro table with UK structure is ebay.nl's, which is the UK table in euros.
            EbaySite.IE -> NETHERLANDS

            EbaySite.PL -> POLAND
        }

    /**
     * The table for a price shown on [site]. Increments follow the listing's own site, so when the price is in
     * another currency (a listing from another site) the table of that currency's site is used.
     */
    fun forPrice(
        site: EbaySite,
        currency: String,
    ): IncrementTable {
        if (currency == site.currency) return forSite(site)
        return when (currency) {
            "GBP" -> UNITED_KINGDOM
            "USD", "CAD", "AUD" -> UNITED_STATES
            "CHF" -> EURO_COARSE
            "PLN" -> POLAND
            "EUR" -> EURO_UNKNOWN_SITE
            else -> forSite(site)
        }
    }
}
