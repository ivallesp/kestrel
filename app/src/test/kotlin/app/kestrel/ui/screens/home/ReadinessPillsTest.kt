package app.kestrel.ui.screens.home

import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.fixture.CatalogueFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The dock's pill in each situation of DESIGN 3.8, with the words of 6.2. */
class ReadinessPillsTest {
    private val now = CatalogueFixture.now
    private val checkedAt = CatalogueFixture.readinessCheckedAt
    private val bidAt = CatalogueFixture.at("2025-11-09T18:21:34Z")

    private fun pill(
        items: List<ReadinessItem>?,
        checking: Boolean = false,
        offline: Boolean = false,
        offlineBidAt: java.time.Instant? = null,
        lotsNeedSignIn: Boolean = false,
    ) = ReadinessPills.of(
        items,
        checkedAt.takeIf { items != null },
        checking,
        offline,
        offlineBidAt,
        lotsNeedSignIn,
        now,
    )

    @Test
    fun allGood() {
        val pill = pill(HomeFixtures.allPassed)
        assertEquals(PillKind.Ready, pill.kind)
        assertEquals(Symbols.CheckCircle, pill.icon)
        assertEquals("Ready to snipe", pill.title)
        assertEquals("All checks passed at 18:06", pill.detail)
        assertNull(pill.action)
    }

    @Test
    fun oneProblemNamesItsConsequenceAndFixesItsOwnPage() {
        val pill = pill(HomeFixtures.batteryRestricted)
        assertEquals(PillKind.OneProblem, pill.kind)
        assertEquals(Symbols.BatteryAlert, pill.icon)
        assertEquals("Battery restricted", pill.title)
        assertEquals("A bid could go out late", pill.detail)
        assertEquals(PillAction.Fix(ReadinessKey.BATTERY), pill.action)
    }

    @Test
    fun severalProblemsAreListedForReview() {
        val pill = pill(HomeFixtures.batteryAndSignedOut)
        assertEquals(PillKind.SeveralProblems, pill.kind)
        assertEquals(Symbols.Warning, pill.icon)
        assertEquals("2 things need attention", pill.title)
        assertEquals("Battery restricted · signed out", pill.detail)
        assertEquals(PillAction.Review, pill.action)
    }

    @Test
    fun signedOutPausesSnipes() {
        val pill = pill(HomeFixtures.signedOut)
        assertEquals(PillKind.SignedOut, pill.kind)
        assertEquals(Symbols.AccountCircle, pill.icon)
        assertEquals("Signed out of eBay", pill.title)
        assertEquals("Snipes are paused", pill.detail)
        assertEquals(PillAction.SignIn, pill.action)
    }

    @Test
    fun aLotThatMetTheSignInPageCountsAsSignedOut() {
        assertEquals(PillKind.SignedOut, pill(HomeFixtures.allPassed, lotsNeedSignIn = true).kind)
    }

    @Test
    fun offlineWithABidWithinTheHourSaysWhichBidCannotGoOut() {
        val pill = pill(HomeFixtures.batteryRestricted, offline = true, offlineBidAt = bidAt)
        assertEquals(PillKind.Offline, pill.kind)
        assertEquals(Symbols.CloudOff, pill.icon)
        assertEquals("Phone offline", pill.title)
        assertEquals("The 18:21:34 bid can’t go out", pill.detail)
        assertNull(pill.action)
    }

    @Test
    fun offlineWithNoBidSoonIsOneMoreProblem() {
        val alone = pill(HomeFixtures.allPassed, offline = true)
        assertEquals(PillKind.Offline, alone.kind)
        assertEquals("Kestrel can’t check or bid", alone.detail)
        val withBattery = pill(HomeFixtures.batteryRestricted, offline = true)
        assertEquals("Battery restricted · offline", withBattery.detail)
    }

    @Test
    fun reCheckingKeepsTheLookAndSaysChecking() {
        val pill = pill(HomeFixtures.batteryRestricted, checking = true)
        assertEquals(PillKind.OneProblem, pill.kind)
        assertEquals("Battery restricted", pill.title)
        assertEquals("Checking…", pill.detail)
        assertEquals(PillAction.Fix(ReadinessKey.BATTERY), pill.action)
    }

    @Test
    fun beforeTheFirstCheckThePillWaits() {
        val pill = pill(items = null, checking = true)
        assertEquals(PillKind.Checking, pill.kind)
        assertEquals("Checking this phone", pill.title)
        assertEquals("Checking…", pill.detail)
    }

    @Test
    fun liveUpdatesAreOptionalAndAStaleNetworkCheckDoesNotCount() {
        val items =
            HomeFixtures.allPassed.map {
                when (it.key) {
                    ReadinessKey.LIVE_UPDATES, ReadinessKey.NETWORK -> it.copy(ok = false)
                    else -> it
                }
            }
        assertEquals(PillKind.Ready, pill(items).kind)
    }

    @Test
    fun aCheckFromAnotherDayNamesItsDay() {
        val pill =
            ReadinessPills.of(
                HomeFixtures.allPassed,
                CatalogueFixture.at("2025-11-08T21:40:00Z"),
                false,
                false,
                null,
                false,
                now,
            )
        assertEquals("All checks passed Sat 8 Nov at 21:40", pill.detail)
    }
}
