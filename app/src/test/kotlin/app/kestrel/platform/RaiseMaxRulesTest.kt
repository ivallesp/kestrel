package app.kestrel.platform

import app.kestrel.data.gbp
import app.kestrel.data.testFormatMoney
import app.kestrel.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class RaiseMaxRulesTest {
    /** Stands in for MoneyText.parseUserInput: digits with an optional decimal point, in the given currency. */
    private val parse: (String, String) -> Money? = { text, currency ->
        text.removePrefix("£").toBigDecimalOrNull()?.let { Money.of(it, currency) }
    }
    private val rules = RaiseMaxRules(parse, ::testFormatMoney)

    private fun decide(
        typed: String?,
        currentMax: Money? = gbp("48.00"),
        currentPrice: Money? = gbp("52.00"),
        minimum: Money? = gbp("53.00"),
        currency: String = "GBP",
    ) = rules.decide(typed, currency, currentMax, currentPrice, minimum)

    @Test
    fun aValidRaiseIsAccepted() {
        assertEquals(RaiseMaxDecision.Accept(gbp("60.00")), decide("60"))
        assertEquals(RaiseMaxDecision.Accept(gbp("60.50")), decide(" £60.50 "))
        assertEquals(RaiseMaxDecision.Accept(gbp("53.00")), decide("53.00"))
    }

    @Test
    fun emptyAndUnreadableRepliesAreRefused() {
        assertEquals(RaiseMaxDecision.Reject("Type the new max, for example 52.50."), decide(null))
        assertEquals(RaiseMaxDecision.Reject("Type the new max, for example 52.50."), decide("   "))
        assertEquals(RaiseMaxDecision.Reject("Couldn't read \"sixty\". Type an amount like 52.50."), decide("sixty"))
    }

    @Test
    fun theNewMaxMustBeAtLeastEbaysMinimum() {
        assertEquals(RaiseMaxDecision.Reject("eBay needs at least GBP 53.00."), decide("52.99"))
    }

    @Test
    fun theNewMaxMustBeMoreThanTheOldOne() {
        assertEquals(
            RaiseMaxDecision.Reject("That isn't more than your max of GBP 48.00."),
            decide("48", minimum = null),
        )
        assertEquals(
            RaiseMaxDecision.Reject("The max must be more than zero."),
            decide("0", currentMax = null, minimum = null),
        )
    }

    @Test
    fun anExtraZeroIsCaughtByTheTenTimesCeiling() {
        assertEquals(
            RaiseMaxDecision.Reject("That is more than 10 times the current price. Open Kestrel to set it."),
            decide("520.01"),
        )
        assertEquals(RaiseMaxDecision.Accept(gbp("520.00")), decide("520.00"))
    }

    @Test
    fun withoutAKnownPriceTheMinimumIsTheReference() {
        assertEquals(RaiseMaxDecision.Accept(gbp("530.00")), decide("530", currentPrice = null))
        assertEquals(
            RaiseMaxDecision.Reject("That is more than 10 times the current price. Open Kestrel to set it."),
            decide("530.01", currentPrice = null),
        )
        assertEquals(RaiseMaxDecision.Accept(gbp("9999.00")), decide("9999", currentPrice = null, minimum = null))
    }

    @Test
    fun aParserThatThrowsIsTreatedAsUnreadable() {
        val throwing = RaiseMaxRules({ _, _ -> throw NumberFormatException("bad") }, ::testFormatMoney)

        assertEquals(
            RaiseMaxDecision.Reject("Couldn't read \"60\". Type an amount like 52.50."),
            throwing.decide("60", "GBP", gbp("48.00"), gbp("52.00"), null),
        )
    }

    @Test
    fun anAmountInAnotherCurrencyIsRefused() {
        val wrongCurrency = RaiseMaxRules({ text, _ -> Money.of(BigDecimal(text), "USD") }, ::testFormatMoney)

        assertEquals(
            RaiseMaxDecision.Reject("Type the amount in GBP."),
            wrongCurrency.decide("60", "GBP", gbp("48.00"), gbp("52.00"), null),
        )
    }
}
