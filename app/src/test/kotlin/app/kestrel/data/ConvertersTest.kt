package app.kestrel.data

import app.kestrel.model.EbaySite
import app.kestrel.model.Money
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun moneyRoundTripsThroughMinorUnits() {
        val amounts = listOf(Money(4850, "GBP"), Money(0, "USD"), Money(-125, "EUR"), Money(Long.MAX_VALUE, "CHF"))
        for (amount in amounts) {
            assertEquals(amount, converters.textToMoney(converters.moneyToText(amount)))
        }
        assertEquals("GBP:4850", converters.moneyToText(Money(4850, "GBP")))
    }

    @Test
    fun nullMoneyStaysNull() {
        assertNull(converters.moneyToText(null))
        assertNull(converters.textToMoney(null))
    }

    @Test
    fun damagedMoneyIsNullRatherThanAnotherAmount() {
        for (text in listOf(
            "GBP4850",
            "gbp:4850",
            "GB:4850",
            "GBPX:4850",
            "GBP:",
            "GBP:48.50",
            ":4850",
            "GBP:12x",
            "",
        )) {
            assertNull("'$text' must not parse", converters.textToMoney(text))
        }
    }

    @Test
    fun enumsRoundTripByName() {
        for (site in EbaySite.entries) assertEquals(site, converters.textToSite(converters.siteToText(site)))
        for (status in SnipeStatus.entries) {
            assertEquals(
                status,
                converters.textToStatus(converters.statusToText(status)),
            )
        }
        for (outcome in SnipeOutcome.entries) {
            assertEquals(
                outcome,
                converters.textToOutcome(converters.outcomeToText(outcome)),
            )
        }
        for (kind in TimelineKind.entries) {
            assertEquals(
                kind,
                converters.textToTimelineKind(converters.timelineKindToText(kind)),
            )
        }
        assertEquals("BID_SENT", converters.statusToText(SnipeStatus.BID_SENT))
    }

    @Test
    fun unknownEnumNamesFailLoudly() {
        assertThrows(IllegalStateException::class.java) { converters.textToSite("MOON") }
        assertThrows(IllegalStateException::class.java) { converters.textToStatus("ARMED_TWICE") }
    }
}
