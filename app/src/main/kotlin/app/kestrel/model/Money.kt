package app.kestrel.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An amount in minor units (pence, cents) of an ISO 4217 currency. Every eBay auction currency Kestrel supports
 * (GBP, USD, EUR, CAD, AUD, CHF, PLN) has two decimals, so minor units are always hundredths.
 */
@Serializable
data class Money(
    val minor: Long,
    val currency: String,
) : Comparable<Money> {
    val major: BigDecimal get() = BigDecimal.valueOf(minor, 2)

    operator fun plus(other: Money): Money = Money(minor + sameCurrency(other).minor, currency)

    operator fun minus(other: Money): Money = Money(minor - sameCurrency(other).minor, currency)

    override fun compareTo(other: Money): Int = minor.compareTo(sameCurrency(other).minor)

    fun isZero(): Boolean = minor == 0L

    private fun sameCurrency(other: Money): Money {
        require(other.currency == currency) { "Currency mismatch: $currency vs ${other.currency}" }
        return other
    }

    companion object {
        fun of(
            major: BigDecimal,
            currency: String,
        ): Money = Money(major.setScale(2, RoundingMode.HALF_UP).unscaledValue().toLong(), currency)

        fun of(
            major: Double,
            currency: String,
        ): Money = of(BigDecimal.valueOf(major), currency)

        fun of(
            major: String,
            currency: String,
        ): Money = of(BigDecimal(major), currency)

        fun zero(currency: String): Money = Money(0, currency)
    }
}
