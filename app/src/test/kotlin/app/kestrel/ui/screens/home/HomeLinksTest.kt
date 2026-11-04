package app.kestrel.ui.screens.home

import app.kestrel.model.SnipeStatus
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.shell.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where Add, the row buttons and pull to refresh lead; nothing here contacts eBay. */
class HomeLinksTest {
    private val fixture = CatalogueFixture

    @Test
    fun addOpensWithAListingOnTheClipboard() {
        assertEquals(
            "https://www.ebay.co.uk/itm/127504318862",
            HomeLinks.linkToAdd("Look at this https://www.ebay.co.uk/itm/127504318862?_trkparms=x"),
        )
        assertEquals("https://ebay.us/m/AbC123", HomeLinks.linkToAdd("https://ebay.us/m/AbC123"))
    }

    @Test
    fun addOpensAtItsLinkEntryOtherwise() {
        assertNull(HomeLinks.linkToAdd(null))
        assertNull(HomeLinks.linkToAdd("   "))
        assertNull(HomeLinks.linkToAdd("Milk, eggs, bread"))
        assertNull(HomeLinks.linkToAdd("https://www.ebay.co.uk/sch/i.html?_nkw=walkman"))
    }

    @Test
    fun rowButtonsOpenTheirScreens() {
        val omega = fixture.omegaOverMax.status
        assertEquals(Destination.SignIn, HomeLinks.destinationFor(1, LotAction.SIGN_IN, null))
        assertEquals(Destination.HandOff(1), HomeLinks.destinationFor(1, LotAction.OPEN_EBAY_PAGE, null))
        assertEquals(Destination.HandOff(1), HomeLinks.destinationFor(1, LotAction.OPEN_ON_EBAY, null))
        assertEquals(Destination.Lot(2), HomeLinks.destinationFor(2, LotAction.RAISE_MAX, omega))
        assertEquals(Destination.Lot(5), HomeLinks.destinationFor(5, LotAction.SET_MAX, null))
        assertEquals(Destination.Result(7), HomeLinks.destinationFor(7, LotAction.VIEW_RECEIPT, null))
    }

    @Test
    fun aDryRunThatMetTheSignInPageIsFixedBySigningIn() {
        val signInPage = fixture.canonDryRunFailed
        assertEquals(Destination.SignIn, HomeLinks.destinationFor(1, LotAction.FIX, signInPage.status))
    }

    @Test
    fun pullToRefreshAsksOnlyForLotsNotCheckedInFiveMinutes() {
        val now = fixture.now.toEpochMilli()
        val due = HomeRefresh.due(fixture.snipes, emptyMap(), now)
        // The Canon was checked at 18:00:12 and the Omega at 18:02:47, both more than five minutes before 18:08:33.
        assertEquals(
            listOf(fixture.canon, fixture.omega, fixture.miniDisc, fixture.walkman, fixture.polaroid, fixture.gameBoy)
                .map { it.id },
            due.map { it.id },
        )
        val justChecked = fixture.snipes.map { it.copy(lastCheckedAtUtcMillis = now - 4 * 60_000) }
        assertTrue(HomeRefresh.due(justChecked, emptyMap(), now).isEmpty())
    }

    @Test
    fun aSecondPullWithinFiveMinutesAsksForNothingMore() {
        val now = fixture.now.toEpochMilli()
        val asked = HomeRefresh.due(fixture.snipes, emptyMap(), now).associate { it.id to now }
        assertTrue(HomeRefresh.due(fixture.snipes, asked, now + 4 * 60_000).isEmpty())
        assertEquals(6, HomeRefresh.due(fixture.snipes, asked, now + 5 * 60_000).size)
    }

    @Test
    fun lotsInTheirBidWindowAreLeftToTheWindow() {
        val now = fixture.at("2025-11-09T18:19:41Z").toEpochMilli()
        val bidding = fixture.canon.copy(status = SnipeStatus.BIDDING)
        val armedLate = fixture.canon.copy(id = 99)
        assertTrue(HomeRefresh.due(listOf(bidding, armedLate), emptyMap(), now).isEmpty())
    }
}
