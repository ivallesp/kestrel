package app.kestrel.ui.shell

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
