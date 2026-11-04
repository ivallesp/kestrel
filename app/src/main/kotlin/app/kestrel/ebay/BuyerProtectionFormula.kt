package app.kestrel.ebay

/**
 * ebay.co.uk Buyer Protection fee for UK private sellers, in pence (docs/research/rules.md BUY4, current since
 * 2025-07-17): £0.10 + 7% of the price up to £20 + 4% of the part from £20 to £300 + 2% of the part from £300 to
 * £4,000, so at most £86.70. The total is rounded to the nearest penny, half up, which reproduces eBay's examples
 * (£5 -> £0.45, £20 -> £1.50, £1,500 -> £36.70, £7,500 -> £86.70, £191.83 -> £8.37).
 */
internal object BuyerProtectionFormula {
    const val CURRENCY = "GBP"

    fun feeMinor(itemPriceMinor: Long): Long {
        if (itemPriceMinor <= 0) return 0
        // In hundredths of a penny, so every percentage stays an exact integer before the single rounding.
        val hundredthsOfPenny =
            FLAT_FEE_MINOR * 100 +
                7 * itemPriceMinor.coerceAtMost(FIRST_BAND_END) +
                4 * (itemPriceMinor.coerceAtMost(SECOND_BAND_END) - FIRST_BAND_END).coerceAtLeast(0) +
                2 * (itemPriceMinor.coerceAtMost(THIRD_BAND_END) - SECOND_BAND_END).coerceAtLeast(0)
        return (hundredthsOfPenny + 50) / 100
    }

    /**
     * The item price inside a fee-inclusive displayed price: the largest price whose price plus fee does not exceed
     * [displayedMinor]. Price plus fee rises by at least a penny per penny of price, so for any displayed amount eBay
     * produced this returns exactly the price it came from.
     */
    fun itemPriceMinor(displayedMinor: Long): Long {
        if (displayedMinor <= FLAT_FEE_MINOR) return 0
        // Start from the algebraic inverse of each band (rules.md BUY4), then settle on the exact penny.
        var price =
            when {
                displayedMinor <= displayedAt(FIRST_BAND_END) -> (displayedMinor - FLAT_FEE_MINOR) * 100 / 107
                displayedMinor <= displayedAt(SECOND_BAND_END) -> (displayedMinor - 70) * 100 / 104
                displayedMinor <= displayedAt(THIRD_BAND_END) -> (displayedMinor - 670) * 100 / 102
                else -> displayedMinor - feeMinor(THIRD_BAND_END)
            }.coerceAtLeast(0)
        while (price > 0 && displayedAt(price) > displayedMinor) price--
        while (displayedAt(price + 1) <= displayedMinor) price++
        return price
    }

    private fun displayedAt(itemPriceMinor: Long): Long = itemPriceMinor + feeMinor(itemPriceMinor)

    private const val FLAT_FEE_MINOR = 10L
    private const val FIRST_BAND_END = 2_000L
    private const val SECOND_BAND_END = 30_000L
    private const val THIRD_BAND_END = 400_000L
}
