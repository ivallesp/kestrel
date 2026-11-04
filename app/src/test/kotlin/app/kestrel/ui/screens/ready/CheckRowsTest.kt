package app.kestrel.ui.screens.ready

import app.kestrel.platform.NotificationChannels
import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.format.forDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S5's rows from the platform's own verdicts and words (DESIGN S5, 3.9 and 6.2). */
class CheckRowsTest {
    private val fixture = CatalogueFixture
    private val now = ReadyFixtures.now

    private fun rows(
        phone: FakePhone = FakePhone(),
        snipes: List<app.kestrel.model.Snipe> = fixture.snipes,
    ): Map<ReadinessKey, CheckRow> {
        val nextBid = CheckRows.nextBid(snipes, now)
        return CheckRows.build(phone.items(), ReadyFixtures.facts(phone), nextBid, now).associateBy { it.key }
    }

    @Test
    fun theDesignFixtureReadsAsDesignSpecifies() {
        val rows = rows()
        val battery = rows.getValue(ReadinessKey.BATTERY)
        assertEquals(CheckStanding.Problem, battery.standing)
        assertEquals("Optimised", battery.stateWords)
        assertEquals("Android may pause Kestrel when the screen is off, so a bid could go out late.", battery.detail)
        assertEquals(CheckAction.SystemPage(ReadinessKey.BATTERY, "Fix"), battery.action)
        assertEquals(Symbols.BatteryAlert, battery.icon)

        val live = rows.getValue(ReadinessKey.LIVE_UPDATES)
        assertEquals(CheckStanding.Optional, live.standing)
        assertEquals("Off · optional", live.stateWords)
        assertEquals("Shows the countdown in the status bar and on the lock screen.", live.detail)
        assertEquals("Turn on", live.action?.label)

        assertEquals(
            "Signed in as oakshelf\u00A0· checked 2\u202Fmin ago",
            rows.getValue(ReadinessKey.EBAY_SESSION).detail,
        )
        assertEquals("On", rows.getValue(ReadinessKey.NOTIFICATIONS).detail)
        assertEquals("Allowed, so bids leave on the second", rows.getValue(ReadinessKey.EXACT_ALARMS).detail)
        assertEquals(
            "0.04\u202Fs off Google\u00A0time\u00A0· synced 1\u202Fmin ago",
            rows.getValue(ReadinessKey.CLOCK).detail,
        )
        assertEquals(
            "Passed today at 18:11 on the Canon AE-\u20601 Program\u00A0· nothing was sent",
            rows.getValue(ReadinessKey.DRY_RUN).detail,
        )
        val passed = rows.values.filter { it.standing == CheckStanding.Passed }
        assertTrue(passed.all { it.action == null && it.stateWords == null })
        assertEquals(1, rows.values.count { it.standing == CheckStanding.Problem })
    }

    @Test
    fun aSignedOutPhoneAsksToSignIn() {
        val session = rows(FakePhone(signedIn = false)).getValue(ReadinessKey.EBAY_SESSION)
        assertEquals(CheckStanding.Problem, session.standing)
        assertEquals("Signed out", session.stateWords)
        assertEquals(CheckAction.SignIn, session.action)
        assertEquals("Sign in", session.action?.label)
        assertTrue(session.detail, session.detail.startsWith("Not signed in to ebay.co.uk."))
    }

    @Test
    fun withNothingArmedTheDryRunIsNotYetDueAndNotCounted() {
        val dryRun = rows(snipes = ReadyFixtures.notArmedSnipes).getValue(ReadinessKey.DRY_RUN)
        assertEquals(CheckStanding.NotYet, dryRun.standing)
        assertEquals("Runs about 10 minutes before each bid", dryRun.detail)
        assertNull(dryRun.action)
    }

    @Test
    fun aFailedDryRunOpensItsSnipe() {
        val dryRun = rows(FakePhone(dryRunPassed = false)).getValue(ReadinessKey.DRY_RUN)
        assertEquals(CheckStanding.Problem, dryRun.standing)
        assertEquals("Failed", dryRun.stateWords)
        assertEquals("Failed today at 18:11 on the Canon AE-\u20601 Program\u00A0· nothing was sent", dryRun.detail)
        assertEquals(CheckAction.OpenSnipe(fixture.canon.id), dryRun.action)
    }

    @Test
    fun aCheckThePlatformCouldNotReadSaysSo() {
        val item = ReadinessItem(ReadinessKey.BATTERY, ok = false, detail = "Couldn't check this on this phone.")
        val row = CheckRows.build(listOf(item), ReadyFixtures.facts(FakePhone()), nextBid = null, now = now).single()
        assertEquals(CheckStanding.Problem, row.standing)
        assertEquals("Couldn’t check", row.stateWords)
        assertEquals("Couldn’t check this on this phone.", row.detail)
        assertEquals(CheckAction.SystemPage(ReadinessKey.BATTERY, "Fix"), row.action)
    }

    @Test
    fun thePlatformsQuotesBecomeCurly() {
        val blocked = FakePhone(blockedChannels = listOf(NotificationChannels.ATTENTION))
        val notifications = rows(blocked).getValue(ReadinessKey.NOTIFICATIONS)
        assertEquals(CheckStanding.Problem, notifications.standing)
        assertEquals("“Needs you” turned off.", notifications.detail)
        val paused = rows().getValue(ReadinessKey.UNUSED_APP_PAUSE)
        assertEquals("Off, so Kestrel stays armed even if you don’t open it", paused.detail)
    }

    @Test
    fun everyRowUsesCurlyQuotesAndProperEbayCasing() {
        val phones =
            listOf(
                FakePhone(),
                FakePhone(signedIn = false, online = false, exactAlarms = false, notificationsOn = false),
                FakePhone(batteryUnrestricted = true, promotedNotifications = true, dryRunPassed = false),
                FakePhone(blockedChannels = NotificationChannels.essential, clockOffsetMillis = 125_000),
            )
        phones.flatMap { rows(it).values }.forEach { row ->
            listOfNotNull(row.name, row.stateWords, row.detail, row.action?.label).forEach { text ->
                assertFalse(text, text.contains('\'') || text.contains('"'))
                assertFalse(
                    text,
                    Regex("""\b(EBAY|Ebay|EBay|ebay)\b""").containsMatchIn(text.replace("ebay.co.uk", "")),
                )
            }
        }
    }

    @Test
    fun theNextBidIsTheEarliestArmedOne() {
        val next = CheckRows.nextBid(fixture.snipes, now)
        assertEquals("Canon AE-1 Program".forDisplay(), next?.title)
        assertEquals(fixture.at("2025-11-09T18:21:34Z"), next?.at)
        assertNull(CheckRows.nextBid(ReadyFixtures.notArmedSnipes, now))
    }

    @Test
    fun shortTitlesStopAtTheFirstSeparatorAndKeepModelNumbersWhole() {
        val cases =
            mapOf(
                "Canon AE-1 Program + 50mm f/1.8" to "Canon AE-1 Program",
                "Nintendo Game Boy DMG-01, boxed" to "Nintendo Game Boy DMG-01",
                "Game & Watch Donkey Kong II" to "Game & Watch Donkey Kong",
                "Omega Speedmaster “Pre-moon” 1960s" to "Omega Speedmaster “Pre-moon” 1960s",
                "Sony Alpha A700 body (with charger)" to "Sony Alpha A700 body",
            )
        cases.forEach { (title, short) -> assertEquals(title, short.forDisplay(), CheckRows.shortTitle(title)) }
        // A word joiner keeps "AE-1" on one line.
        assertTrue(CheckRows.shortTitle("Canon AE-1 Program").contains("AE-\u20601"))
    }

    @Test
    fun theDeadlineNamesTheBidAndTheLot() {
        val canon = NextBid("Canon AE-1 Program", fixture.at("2025-11-09T18:21:34Z"))
        assertEquals(
            "Fix it before 18:21:34, when Kestrel bids on your Canon AE-1 Program.",
            deadlineSentence(1, canon, now),
        )
        assertEquals(
            "Fix them before 18:21:34, when Kestrel bids on your Canon AE-1 Program.",
            deadlineSentence(2, canon, now),
        )
        val miniDisc = NextBid("Sony MiniDisc Walkman MZ-N707", fixture.at("2025-11-10T20:10:05Z"))
        assertEquals(
            "Fix it before Mon 10 Nov at 20:10, when Kestrel bids on your Sony MiniDisc Walkman MZ-N707.",
            deadlineSentence(1, miniDisc, now),
        )
    }
}
