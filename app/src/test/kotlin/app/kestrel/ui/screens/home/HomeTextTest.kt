package app.kestrel.ui.screens.home

import app.kestrel.ui.components.SmallCapsRules
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.format.NARROW_NO_BREAK_SPACE
import app.kestrel.ui.format.NO_BREAK_SPACE
import app.kestrel.ui.format.absoluteTime
import app.kestrel.ui.format.timeLeft
import app.kestrel.ui.model.spokenLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The words S1 adds around the status words (DESIGN 3.3 to 3.6, 6.3), against the fixture. */
class HomeTextTest {
    private val fixture = CatalogueFixture
    private val now = fixture.now
    private val home = HomeFixtures.home

    private fun lot(id: Long) =
        home.sections
            .flatMap { it.blocks }
            .flatMap { it.lots }
            .single { it.id == id }

    /** The text as read, with the no-break spaces of tabular runs and units back to plain spaces. */
    private fun plain(text: CharSequence?) =
        text.toString().replace(NO_BREAK_SPACE, ' ').replace(NARROW_NO_BREAK_SPACE, ' ')

    @Test
    fun theOverlineNamesTheDayAndTheZone() {
        assertEquals("SUNDAY 9 NOVEMBER · GMT", HomeText.overline(now))
        assertEquals("THURSDAY 3 JULY · BST", HomeText.overline(fixture.at("2025-07-03T09:00:00Z")))
        assertEquals("WEDNESDAY 24 SEPTEMBER · BST", HomeText.overline(fixture.at("2025-09-24T09:00:00Z")))
    }

    @Test
    fun everyOverlineOfTheYearFitsTheSmallCapsRules() {
        var day = LocalDate.of(2025, 1, 1)
        while (day.year == 2025) {
            val overline = HomeText.overline(day.atTime(12, 0).toInstant(ZoneOffset.UTC))
            assertTrue(overline, SmallCapsRules.allows(overline))
            day = day.plusDays(1)
        }
    }

    @Test
    fun rowTimeLinesAreTheAbsoluteTimeThenTheTimeLeft() {
        listOf(fixture.canon, fixture.omega, fixture.miniDisc, fixture.walkman, fixture.polaroid, fixture.gameBoy)
            .forEach { snipe ->
                val end = Instant.ofEpochMilli(snipe.endTimeUtcMillis)
                val expected = "${absoluteTime(now, end)} · in ${timeLeft(now, end)}"
                assertEquals(snipe.title, plain(expected), plain(HomeText.timeLine(lot(snipe.id), now)))
            }
        assertEquals("Today 18:21:40 GMT · in 13 m 07 s", plain(HomeText.timeLine(lot(fixture.canon.id), now)))
        assertEquals("Today 21:02 · in 2 h 53 m", plain(HomeText.timeLine(lot(fixture.omega.id), now)))
        assertEquals("Mon 10 Nov, 20:10 · in 1 d 2 h", plain(HomeText.timeLine(lot(fixture.miniDisc.id), now)))
    }

    @Test
    fun theTimeLineCarriesTabularFiguresOnlyOnItsFigures() {
        val line = HomeText.timeLine(lot(fixture.omega.id), now)
        val figures = line.spanStyles.map { line.text.substring(it.start, it.end) }
        assertEquals(listOf("21:02", "2", "53"), figures)
    }

    @Test
    fun pricesShowBidsAndPostage() {
        assertEquals("5 bids · £3.49 postage", HomeText.priceMeta(lot(fixture.miniDisc.id), now))
        assertEquals("31 bids · £12.95 postage", HomeText.priceMeta(lot(fixture.omega.id), now))
        assertEquals("£24.00", HomeText.price(lot(fixture.miniDisc.id)))
        val offline =
            HomeFixtures.offline.sections
                .flatMap { it.blocks }
                .flatMap { it.lots }
                .single { it.id == fixture.miniDisc.id }
        assertEquals("5 bids · £3.49 postage · as of 18:02", HomeText.priceMeta(offline, now))
        assertEquals("free postage", HomeText.postageWords(fixture.gbp("0.00")))
        assertEquals("postage not listed", HomeText.postageWords(null))
    }

    @Test
    fun theWalkmanSaysItUsesExtendedBidding() {
        val flags = HomeText.flags(lot(fixture.walkman.id), now)
        assertEquals(listOf("Extended bidding: Kestrel’s bid will reset the clock to 60 s."), flags.map { it.text })
        assertEquals("Extended bidding: Kestrel’s bid will reset the clock to 60 seconds.", flags.single().spoken)
    }

    @Test
    fun finishedRowsSayWhatTheyWereWonOrSoldFor() {
        val won = lot(fixture.gameAndWatch.id)
        assertEquals(EndedPrice("Won for", "£34.00", "+ £3.10 postage"), HomeText.endedPrice(won))
        assertEquals("£11.00 under your £45.00 max", HomeText.endedNote(won, now))
        val lost = lot(fixture.nintendo64.id)
        assertEquals(EndedPrice("Sold for", "£79.00"), HomeText.endedPrice(lost))
        assertEquals("Your max was £65.00", HomeText.endedNote(lost, now))
    }

    @Test
    fun otherFinishedRowsUseTheirDetailLine() {
        val notSent = HomeFixtures.lotOf(fixture.canonBidNotSent)
        assertEquals(EndedPrice("Sold for", "£104.00"), HomeText.endedPrice(notSent))
        assertEquals(
            "eBay signed you out at 18:21:20. Nothing was bid.",
            HomeText.endedNote(notSent, fixture.canonBidNotSent.now),
        )
        val endedEarly = HomeFixtures.lotOf(fixture.gameBoyEndedEarly)
        assertNull(HomeText.endedPrice(endedEarly))
        assertEquals("The seller ended the listing early. Nothing was bid.", HomeText.endedNote(endedEarly, now))
    }

    @Test
    fun aRowIsOneSentenceForTalkBack() {
        assertEquals(
            "Omega Speedmaster Pre-moon 1960s. Needs attention: £250.00 over your £1,900.00 max. " +
                "Current bid £2,150.00, 31 bids, postage £12.95. Ends today at 21:02, in 2 hours 53 minutes.",
            HomeText.spokenRow(lot(fixture.omega.id), now),
        )
        assertEquals(
            "Game & Watch Donkey Kong II. Won, Sat 8 Nov. Won for £34.00 plus £3.10 postage. " +
                "£11.00 under your £45.00 max.",
            HomeText.spokenRow(lot(fixture.gameAndWatch.id), now),
        )
        assertEquals(
            "Sony Walkman WM-FX421. Armed, max £40.00. Current bid £18.50, 3 bids, postage £3.20. " +
                "Ends Tue 11 Nov at 19:55, in 2 days 1 hour. " +
                "Extended bidding: Kestrel’s bid will reset the clock to 60 seconds.",
            HomeText.spokenRow(lot(fixture.walkman.id), now),
        )
    }

    @Test
    fun everyStatusIsSpokenWithTheModelsWords() {
        HomeFixtures.statusMoments.forEach { moment ->
            val lot = HomeFixtures.lotOf(moment)
            val spoken = HomeText.spokenRow(lot, moment.now)
            assertTrue(spoken, spoken.contains(lot.status.spokenLabel(moment.now)))
            assertFalse(spoken, spoken.contains('"') || spoken.contains('\''))
        }
    }

    @Test
    fun theHeroTimeBlockSaysWhenItEndsAndWhenKestrelBids() {
        val canon = lot(fixture.canon.id)
        val end = Instant.ofEpochMilli(fixture.canon.endTimeUtcMillis)
        assertEquals("13 m${NO_BREAK_SPACE}07 s".replace(NO_BREAK_SPACE, ' '), HomeText.heroCountdown(now, end))
        assertEquals("18:21:40", HomeText.heroClock(now, end))
        assertEquals("Today · GMT", HomeText.heroDayAndZone(now, end))
        assertEquals("Bids at 18:21:34, 6 s before", plain(HomeText.heroLine(canon, now)))
        assertEquals(
            "Ends in 13 minutes 7 seconds, at 18:21:40 GMT today. Kestrel bids at 18:21:34, 6 seconds before the end.",
            HomeText.heroSpokenTime(canon, now),
        )
        val miniDisc = lot(fixture.miniDisc.id)
        // The MiniDisc ends at 20:10:05, so its 6 s lead bids at 20:09:59.
        assertEquals("Bids Mon 10 Nov at 20:09", plain(HomeText.heroLine(miniDisc, now)))
        assertEquals(
            "Mon 10 Nov · GMT",
            HomeText.heroDayAndZone(now, Instant.ofEpochMilli(fixture.miniDisc.endTimeUtcMillis)),
        )
    }

    @Test
    fun theFinalMinuteHeroLineTicksInTabularFigures() {
        val moment = fixture.canonFinalMinute
        val hero = HomeFixtures.lotOf(moment)
        val line = HomeText.heroLine(hero, moment.now)
        assertEquals("Kestrel bids £120.00 in 30 s", plain(line))
        assertEquals(listOf("30"), line.spanStyles.map { line.text.substring(it.start, it.end) })
    }

    @Test
    fun theHeroLineIsOneShortLineInEveryBiddingState() {
        val lines =
            listOf(
                fixture.canonBidSent to "Bid sent · waiting for eBay",
                fixture.canonInTheLead to "In the lead · not won until 18:21:40",
                fixture.canonOutbid to "Outbid · another max is higher",
                fixture.canonSignInNeeded to "Sign in before 18:21:34 to keep this bid",
            )
        lines.forEach { (moment, expected) ->
            assertEquals(expected, plain(HomeText.heroLine(HomeFixtures.lotOf(moment), moment.now)))
        }
    }

    @Test
    fun groupsSayWhatHappensOnceOneIsWon() {
        val two = GroupHeader(1, "Portable audio", targetWins = 1, memberCount = 2)
        assertEquals("Portable audio: win 1 of 2", HomeText.groupTitle(two))
        assertEquals("Once one is won, Kestrel cancels the other.", HomeText.groupDetail(two))
        assertEquals("Once one is won, Kestrel cancels the rest.", HomeText.groupDetail(two.copy(memberCount = 3)))
        assertEquals(
            "Once 2 are won, Kestrel cancels the rest.",
            HomeText.groupDetail(two.copy(targetWins = 2, memberCount = 4)),
        )
    }

    @Test
    fun snackbarsUseTheFirstTwoWords() {
        assertEquals("Omega Speedmaster", HomeText.shortTitle(fixture.omega.title))
        assertEquals("Canon AE-1", HomeText.shortTitle(fixture.canon.title))
        assertEquals("Game & Watch", HomeText.shortTitle(fixture.gameAndWatch.title))
        assertEquals("Nintendo 64", HomeText.shortTitle(fixture.nintendo64.title))
        assertEquals("Polaroid", HomeText.shortTitle("Polaroid"))
    }

    @Test
    fun theAccountButtonNamesTheAccount() {
        assertEquals("Settings and eBay account, oakshelf", HomeText.accountDescription(fixture.ACCOUNT))
        assertEquals("Settings and eBay account, signed out", HomeText.accountDescription(null))
    }
}
