package app.kestrel.ui.format

import app.kestrel.ebay.BidIncrements
import app.kestrel.model.EbaySite
import app.kestrel.model.Money
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.CatalogueFixture.at
import app.kestrel.ui.fixture.CatalogueFixture.gbp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FormattersTest {
    private val now = CatalogueFixture.now
    private val thin = NARROW_NO_BREAK_SPACE

    @Test
    fun moneyHasTheSymbolSeparatorsAndPence() {
        assertEquals("£2,150.00", gbp("2150").formatted())
        assertEquals("£72.37", gbp("72.37").formatted())
        assertEquals("£0.05", Money(5, "GBP").formatted())
    }

    @Test
    fun countdownJoinsNumberAndUnitWithANarrowNoBreakSpace() {
        assertEquals("4${thin}d 1${thin}h", Duration.ofMinutes(4 * 1_440 + 115).countdown())
        assertEquals("2${thin}h 53${thin}m", Duration.ofSeconds(2 * 3_600 + 53 * 60 + 42).countdown())
        assertEquals("13${thin}m 07${thin}s", Duration.ofSeconds(13 * 60 + 7).countdown())
        assertEquals("36${thin}s", Duration.ofSeconds(36).countdown())
        assertEquals("0${thin}s", Duration.ofSeconds(-3).countdown())
    }

    @Test
    fun countdownsOfTheFixtureMatchTheDesign() {
        assertEquals("13${thin}m 07${thin}s", timeLeft(now, at("2025-11-09T18:21:40Z")))
        assertEquals("2${thin}h 53${thin}m", timeLeft(now, at("2025-11-09T21:02:15Z")))
        assertEquals("1${thin}d 2${thin}h", timeLeft(now, at("2025-11-10T20:10:05Z")))
        assertEquals("2${thin}d 1${thin}h", timeLeft(now, at("2025-11-11T19:55:48Z")))
        assertEquals("4${thin}d 1${thin}h", timeLeft(now, at("2025-11-13T19:30:00Z")))
        assertEquals("7${thin}d 3${thin}h", timeLeft(now, at("2025-11-16T21:43:52Z")))
    }

    @Test
    fun spokenDurationsUseWords() {
        assertEquals("13 minutes 7 seconds", Duration.ofSeconds(13 * 60 + 7).spoken())
        assertEquals("1 second", Duration.ofSeconds(1).spoken())
        assertEquals("1 day 2 hours", Duration.ofHours(26).spoken())
    }

    @Test
    fun secondsAppearOnlyWithinTheHourAndThenWithTheZone() {
        assertEquals("Today 18:21:40 GMT", absoluteTime(now, at("2025-11-09T18:21:40Z")))
        assertEquals("Today 21:02", absoluteTime(now, at("2025-11-09T21:02:15Z")))
        assertEquals("Mon 10 Nov, 20:10", absoluteTime(now, at("2025-11-10T20:10:05Z")))
        assertEquals("18:21:34", clockFor(now, at("2025-11-09T18:21:34Z")))
        assertEquals("21:02", clockFor(now, at("2025-11-09T21:02:09Z")))
    }

    @Test
    fun aMomentWithinTheHourOnAnotherDayKeepsTheDayAndTheSeconds() {
        val lateEvening = at("2025-11-09T23:40:00Z")
        assertEquals("Mon 10 Nov, 00:10:05 GMT", absoluteTime(lateEvening, at("2025-11-10T00:10:05Z")))
    }

    @Test
    fun todayOnlyForTodayAndNeverTomorrowOrYesterday() {
        assertEquals("Today", dayLabel(now, at("2025-11-09T23:59:00Z")))
        assertEquals("Mon 10 Nov", dayLabel(now, at("2025-11-10T00:01:00Z")))
        assertEquals("Sat 8 Nov", dayLabel(now, at("2025-11-08T20:14:09Z")))
        val labels = listOf("2025-11-08T20:14:09Z", "2025-11-10T00:01:00Z").map { absoluteTime(now, at(it)) }
        assertFalse(
            labels.any {
                it.contains("tomorrow", ignoreCase = true) ||
                    it.contains("yesterday", ignoreCase = true)
            },
        )
    }

    @Test
    fun everyFixtureWeekdayMatchesItsInstant() {
        val days =
            mapOf(
                "2025-11-05T21:12:44Z" to "Wed 5 Nov",
                "2025-11-07T19:41:00Z" to "Fri 7 Nov",
                "2025-11-08T20:14:09Z" to "Sat 8 Nov",
                "2025-11-09T18:08:33Z" to "Sun 9 Nov",
                "2025-11-10T20:10:05Z" to "Mon 10 Nov",
                "2025-11-11T19:55:48Z" to "Tue 11 Nov",
                "2025-11-13T19:30:00Z" to "Thu 13 Nov",
                "2025-11-16T21:43:52Z" to "Sun 16 Nov",
            )
        days.forEach { (instant, day) -> assertEquals(day, at(instant).dayDate()) }
        assertEquals("Sunday 9 November", now.longDate())
    }

    @Test
    fun winterIsGmtAndSummerIsBst() {
        assertEquals("GMT", now.zoneName())
        val summer = Instant.parse("2025-07-01T12:00:00Z")
        assertEquals("BST", summer.zoneName())
        assertEquals("13:00", summer.clock())
        assertEquals("TUESDAY 1 JULY · TIMES IN BST", zoneOverline(summer))
    }

    @Test
    fun theOverlineNamesTheDayAndTheZone() {
        assertEquals("SUNDAY 9 NOVEMBER · TIMES IN GMT", zoneOverline(now))
    }

    @Test
    fun theArmSummaryAlwaysHasSecondsAndTheZone() {
        val bidAt = at("2025-11-13T20:04:12Z")
        assertEquals("Bids £90.00 on Thu 13 Nov at 20:04:12 GMT", armSummary(gbp("90.00"), bidAt, now))
        assertEquals("Bids £120.00 today at 18:21:34 GMT", armSummary(gbp("120.00"), at("2025-11-09T18:21:34Z"), now))
    }

    @Test
    fun receiptsShowTenthsOnTheResultDayAndTheDayOtherwise() {
        val resultDay = at("2025-11-07T19:42:10Z")
        assertEquals("19:40:54.9", receiptTime(at("2025-11-07T19:40:54.950Z"), resultDay))
        assertEquals("19:41:00.0", receiptTime(at("2025-11-07T19:41:00Z"), resultDay))
        assertEquals("Wed 5 Nov 21:12:44", receiptTime(at("2025-11-05T21:12:44Z"), resultDay))
    }

    @Test
    fun secondFractionsUseTheNarrowSpace() {
        assertEquals("6${thin}s", secondsText(6))
        assertEquals("6.1${thin}s", secondsWithTenths(6_100))
        assertEquals("0.8${thin}s", secondsWithTenths(850))
        assertEquals("0.04${thin}s", secondsWithHundredths(40))
        assertEquals("0.04${thin}s", secondsWithHundredths(-38))
    }

    @Test
    fun monthHeadingsShowTheYearOnlyWhenItIsNotThisYear() {
        assertEquals("November", at("2025-11-07T19:41:00Z").monthHeading(now))
        assertEquals("December 2024", at("2024-12-20T10:00:00Z").monthHeading(now))
    }

    @Test
    fun plurals() {
        assertEquals("1 lot", plural(1, "lot", "lots"))
        assertEquals("2 lots", plural(2, "lot", "lots"))
        assertEquals("14 bids", plural(14, "bid", "bids"))
    }

    @Test
    fun titlesKeepModelNumbersWhole() {
        assertEquals(
            "Canon AE-${WORD_JOINER}1 Program + 50mm${NO_BREAK_SPACE}f/${WORD_JOINER}1.8",
            "Canon AE-1 Program + 50mm f/1.8".forDisplay(),
        )
        assertEquals("Sony MiniDisc Walkman MZ-${WORD_JOINER}N707", "Sony MiniDisc Walkman MZ-N707".forDisplay())
        assertEquals("Nintendo Game Boy DMG-${WORD_JOINER}01, boxed", "Nintendo Game Boy DMG-01, boxed".forDisplay())
        assertEquals("Game & Watch Donkey Kong II", "Game & Watch Donkey Kong II".forDisplay())
    }

    @Test
    fun kestrelsOwnWordsGetCurlyQuotes() {
        assertEquals(
            "eBay refused the bid: “Enter £98.00 or more”",
            "eBay refused the bid: \"Enter £98.00 or more\"".withCurlyQuotes(),
        )
        assertEquals("Kestrel’s bid", "Kestrel's bid".withCurlyQuotes())
        assertTrue("‘quoted’".withCurlyQuotes() == "‘quoted’")
    }

    @Test
    fun minimumBidsFollowTheEbayUkIncrements() {
        assertEquals(gbp("2.00"), BidIncrements.increment(EbaySite.UK, gbp("64.00")))
        assertEquals(gbp("50.00"), BidIncrements.increment(EbaySite.UK, gbp("2150.00")))
        assertEquals(gbp("2200.00"), BidIncrements.minimumNextBid(EbaySite.UK, gbp("2150.00"), 31, false))
        assertEquals(gbp("63.00"), BidIncrements.minimumNextBid(EbaySite.UK, gbp("61.00"), 9, false))
        assertEquals(gbp("98.00"), BidIncrements.minimumNextBid(EbaySite.UK, gbp("96.00"), 16, false))
    }
}
