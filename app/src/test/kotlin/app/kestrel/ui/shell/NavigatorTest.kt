package app.kestrel.ui.shell

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    private val stack = mutableListOf<NavKey>(Destination.Home)
    private var exited = false
    private var onboarded = false
    private val navigator = BackStackNavigator(stack, exit = { exited = true }, onboardingDone = { onboarded = true })

    @Test
    fun openPushesAndBackPops() {
        navigator.open(Destination.Lot(3))
        assertEquals(listOf(Destination.Home, Destination.Lot(3)), stack)
        navigator.open(Destination.Lot(3))
        assertEquals("the same screen twice is one screen", 2, stack.size)
        navigator.back()
        assertEquals(listOf(Destination.Home), stack)
    }

    @Test
    fun backFromTheLastScreenLeavesTheApp() {
        navigator.back()
        assertTrue(exited)
        assertEquals(listOf(Destination.Home), stack)
    }

    @Test
    fun replaceSwapsTheTopScreen() {
        navigator.open(Destination.Lot(3))
        navigator.replace(Destination.Result(3))
        assertEquals(listOf(Destination.Home, Destination.Result(3)), stack)
    }

    @Test
    fun resetToLeavesOneScreen() {
        navigator.open(Destination.Settings)
        navigator.open(Destination.History)
        navigator.resetTo(Destination.Home)
        assertEquals(listOf(Destination.Home), stack)
    }

    @Test
    fun completingOnboardingOpensHomeThenWhatTheAppWasOpenedFor() {
        val firstLaunch = mutableListOf<NavKey>(Destination.Onboarding)
        val onboarding =
            BackStackNavigator(
                backStack = firstLaunch,
                exit = {},
                onboardingDone = { onboarded = true },
                afterOnboarding = Destination.Add("x"),
            )
        onboarding.openFromOutside(Destination.Lot(5))
        assertEquals(listOf(Destination.Onboarding), firstLaunch)
        onboarding.completeOnboarding()
        assertTrue(onboarded)
        assertEquals(listOf(Destination.Home, Destination.Lot(5)), firstLaunch)
    }

    @Test
    fun aMessageWaitsForTheNextScreen() =
        runBlocking {
            navigator.leaveMessage(ShellMessage("Armed: bids Thu 13 Nov at 20:04:12"))
            assertEquals("Armed: bids Thu 13 Nov at 20:04:12", navigator.messages.first().text)
        }

    @Test
    fun aScreenThatLeavesAMessageAndGoesBackLeavesItForTheScreenUnderneath() =
        runBlocking {
            navigator.open(Destination.Add("https://www.ebay.co.uk/itm/127504318862"))
            val add = navigator.forEntry(Destination.Add("https://www.ebay.co.uk/itm/127504318862"))
            val home = navigator.forEntry(Destination.Home)
            add.leaveMessage(ShellMessage("Armed: bids Thu 13 Nov at 20:04:12"))
            add.back()
            assertNull(
                "Add is gone, so it takes nothing",
                withTimeoutOrNull(SHORT_WAIT_MILLIS) { add.messages.first() },
            )
            assertEquals("Armed: bids Thu 13 Nov at 20:04:12", home.messages.first().text)
        }

    @Test
    fun aMessageLeftWithoutMovingIsForTheScreenOnTop() =
        runBlocking {
            // The edit sheet over the list saves and closes; the list stays on top and shows its snackbar.
            val home = navigator.forEntry(Destination.Home)
            home.leaveMessage(ShellMessage("Max raised to £2,200.00"))
            assertEquals("Max raised to £2,200.00", home.messages.first().text)
            assertNull(withTimeoutOrNull(SHORT_WAIT_MILLIS) { home.messages.first() })
        }

    @Test
    fun aMessageNoScreenTookIsDroppedByTheNextMove() =
        runBlocking {
            navigator.open(Destination.Onboarding)
            navigator.open(Destination.SignInBrowser)
            navigator.leaveMessage(ShellMessage("Sign-in didn’t finish."))
            navigator.back()
            // Onboarding shows no snackbars; once it moves on, the message is no one's.
            navigator.resetTo(Destination.Home)
            val home = navigator.forEntry(Destination.Home)
            assertNull(withTimeoutOrNull(SHORT_WAIT_MILLIS) { home.messages.first() })
        }

    private companion object {
        const val SHORT_WAIT_MILLIS = 200L
    }
}
