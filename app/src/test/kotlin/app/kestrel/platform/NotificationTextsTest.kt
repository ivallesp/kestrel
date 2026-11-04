package app.kestrel.platform

import app.kestrel.data.TEST_NOW
import app.kestrel.data.gbp
import app.kestrel.data.testFormatMoney
import app.kestrel.data.testSnipe
import app.kestrel.engine.NeedsUserReason
import app.kestrel.engine.WindowPhase
import app.kestrel.model.Money
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class NotificationTextsTest {
    private val texts = testTexts()
    private val longTitle = "Vintage Canon AE-1 Program 35mm SLR film camera with 50mm f/1.8 lens, tested"

    private fun result(
        outcome: SnipeOutcome,
        finalPrice: Money? = null,
        reason: String? = null,
        ebayText: String? = null,
    ) = SnipeResult(outcome, finalPrice, null, null, null, ebayText, reason)

    @Test
    fun timesFollowTheZoneAndTheClockFormat() {
        val london =
            NotificationTexts(::testFormatMoney, zone = { ZoneId.of("Europe/London") }, use24HourClock = { true })
        val twelveHour = NotificationTexts(::testFormatMoney, zone = { ZoneOffset.UTC }, use24HourClock = { false })

        assertEquals("21:00", texts.clockTime(TEST_NOW))
        assertEquals("Sun 21:00", texts.dayAndTime(TEST_NOW))
        assertEquals("21:00", london.clockTime(TEST_NOW))
        assertEquals("9:00 pm", twelveHour.clockTime(TEST_NOW))
    }

    @Test
    fun windowTitleNamesTheMaxAndTheBidTime() {
        val snipe = testSnipe(maxBid = gbp("48.00"))

        assertEquals("Bidding GBP 48.00 at 21:00", texts.windowTitle(snipe, TEST_NOW))
        assertEquals("Bidding GBP 48.00", texts.windowTitle(snipe, bidAtUtcMillis = null))
    }

    @Test
    fun aLongMaxDropsTheTimeRatherThanOverflowTheTitle() {
        val title = texts.windowTitle(testSnipe(maxBid = gbp("12345678.00")), TEST_NOW)

        assertEquals("Bidding GBP 12345678.00", title)
    }

    @Test
    fun windowTextCombinesThePhaseAndTheItem() {
        val snipe = testSnipe(title = "Fender Strat", lastPrice = gbp("31.50"))

        assertEquals("Ready, now GBP 31.50 · Fender Strat", texts.windowText(snipe, WindowPhase.READY, detail = null))
        assertEquals(
            "Extended to 21:05 · Fender Strat",
            texts.windowText(snipe, WindowPhase.EXTENDED, "Extended to 21:05"),
        )
        assertEquals("Getting ready · Fender Strat", texts.preparingText("Fender Strat"))
    }

    @Test
    fun chipShowsBiddingDuringTheBidAndTheOutcomeAtTheEnd() {
        assertNull(texts.chipText(WindowPhase.CHECKING))
        assertNull(texts.chipText(WindowPhase.READY))
        for (phase in listOf(WindowPhase.BIDDING, WindowPhase.BID_SENT, WindowPhase.LEADING, WindowPhase.EXTENDED)) {
            assertEquals("Bidding", texts.chipText(phase))
        }
        assertEquals("Won", texts.chipText(SnipeOutcome.WON))
        assertEquals("Lost", texts.chipText(SnipeOutcome.LOST))
        assertEquals("Ended", texts.chipText(SnipeOutcome.FAILED))
    }

    @Test
    fun everyTitleStaysUnder29Characters() {
        val snipe = testSnipe(title = longTitle, maxBid = gbp("1234.56"))
        val titles =
            SnipeOutcome.entries.map { texts.resultTitle(snipe, result(it, finalPrice = gbp("1131.00"))) } +
                NeedsUserReason.entries.map { texts.needsUserTitle(it) } +
                ScheduleProblem.entries.map { texts.scheduleProblemTitle(it) } +
                SnipeAction.entries.map { texts.tapToArmTitle(it) } +
                SnipeAction.entries.map { texts.serviceTitle(it) } +
                listOf(
                    texts.windowTitle(snipe, TEST_NOW),
                    texts.priceOverMaxTitle(),
                    texts.raiseMaxDoneTitle(gbp("1234.56")),
                    texts.unlockTitle(),
                    texts.preparingTitle(),
                )

        for (title in titles) assertTrue("'$title' is ${title.length} characters", title.length < 29)
    }

    @Test
    fun resultsUseThePlainWordsOfTheDesign() {
        val snipe = testSnipe(title = "Canon AE-1", maxBid = gbp("120.00"))

        assertEquals("Won · Canon AE-1", texts.resultTitle(snipe, result(SnipeOutcome.WON, gbp("85.00"))))
        assertEquals(
            "GBP 85.00, GBP 35.00 under your max",
            texts.resultText(snipe, result(SnipeOutcome.WON, gbp("85.00"))),
        )
        assertEquals("Sold for GBP 131.00 · Canon…", texts.resultTitle(snipe, result(SnipeOutcome.LOST, gbp("131.00"))))
        assertEquals("Your max was GBP 120.00", texts.resultText(snipe, result(SnipeOutcome.LOST, gbp("131.00"))))
        assertEquals("Bid failed · Canon AE-1", texts.resultTitle(snipe, result(SnipeOutcome.FAILED)))
        assertEquals(
            "eBay session expired",
            texts.resultText(snipe, result(SnipeOutcome.FAILED, reason = "eBay session expired")),
        )
        assertEquals(
            "Item sold on Sun",
            texts.resultText(snipe, result(SnipeOutcome.ENDED_EARLY, ebayText = "Item sold on Sun")),
        )
    }

    @Test
    fun badNewsCarriesNoEmoji() {
        val snipe = testSnipe(title = "Canon AE-1")
        val badNews =
            SnipeOutcome.entries.filter { it != SnipeOutcome.WON }.flatMap {
                listOf(texts.resultTitle(snipe, result(it)), texts.resultText(snipe, result(it)))
            } + NeedsUserReason.entries.flatMap { listOf(texts.needsUserTitle(it), texts.needsUserText(it, null)) }

        for (text in badNews) {
            assertTrue(
                text,
                text.codePoints().noneMatch {
                    Character.getType(it) ==
                        Character.OTHER_SYMBOL.toInt()
                },
            )
        }
    }

    @Test
    fun longItemTitlesAreShortenedWithAnEllipsis() {
        assertEquals("Won · Vintage Canon AE-1 Pr…", NotificationTexts.fitWithItem("Won", longTitle))
        assertEquals(28, NotificationTexts.fitWithItem("Won", longTitle).length)
        assertEquals("Result unknown · Vintage Ca…", NotificationTexts.fitWithItem("Result unknown", longTitle))
        assertEquals(
            "A very long prefix that lea…",
            NotificationTexts.fitWithItem("A very long prefix that leaves no room", "item"),
        )
    }

    @Test
    fun priceAlertSaysWhereThePriceAndTheMaxStand() {
        val snipe = testSnipe(maxBid = gbp("48.00"), title = "Canon AE-1")

        assertEquals("Now GBP 52.00, your max is GBP 48.00", texts.priceOverMaxText(snipe, gbp("52.00")))
        assertEquals(
            "Now GBP 52.00, your max is GBP 48.00. Raise it to at least GBP 53.00 to keep bidding.\nCanon AE-1",
            texts.priceOverMaxDetail(snipe, gbp("52.00"), gbp("53.00"), problem = null),
        )
        assertEquals("New max (£)", texts.raiseMaxLabel("GBP"))
        assertEquals("New max (US$)", texts.raiseMaxLabel("USD"))
    }

    @Test
    fun unlockNoticeCountsSnipesAndNamesTheDay() {
        assertEquals("To arm 1 snipe. It ends Sun 21:00.", texts.unlockText(1, TEST_NOW))
        assertEquals("To arm 3 snipes. The next ends Sun 21:00.", texts.unlockText(3, TEST_NOW))
    }

    @Test
    fun tapToArmExplainsWhyAndWhen() {
        assertEquals(
            "Android stopped Kestrel starting · Canon AE-1, ends Sun 21:00",
            texts.tapToArmText("Canon AE-1", TEST_NOW),
        )
        assertEquals("Android stopped Kestrel starting", texts.tapToArmText(null, null))
    }
}
