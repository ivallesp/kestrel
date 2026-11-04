package app.kestrel.ui.screens.onboarding

import app.kestrel.ui.screens.ready.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The first launch: three pages, sign-in, notifications, each skipped once it is done. */
class OnboardingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeSources : OnboardingSources {
        val name = MutableStateFlow<String?>(null)
        var cookie = false

        override fun signedInName(): Flow<String?> = name

        override fun hasSessionCookie(): Boolean = cookie
    }

    @Test
    fun theStepsRunInOrderAndSkipWhatIsDone() {
        val steps = OnboardingSteps
        assertEquals(
            OnboardingStep.YourMax,
            steps.after(OnboardingStep.LastSeconds, signedIn = false, notificationsAllowed = false),
        )
        assertEquals(
            OnboardingStep.ThisPhone,
            steps.after(OnboardingStep.YourMax, signedIn = false, notificationsAllowed = false),
        )
        assertEquals(
            OnboardingStep.SignIn,
            steps.after(OnboardingStep.ThisPhone, signedIn = false, notificationsAllowed = false),
        )
        assertEquals(
            OnboardingStep.Notifications,
            steps.after(OnboardingStep.ThisPhone, signedIn = true, notificationsAllowed = false),
        )
        assertNull(steps.after(OnboardingStep.ThisPhone, signedIn = true, notificationsAllowed = true))
        assertEquals(
            OnboardingStep.Notifications,
            steps.after(OnboardingStep.SignIn, signedIn = true, notificationsAllowed = false),
        )
        assertNull(steps.after(OnboardingStep.Notifications, signedIn = false, notificationsAllowed = false))
    }

    @Test
    fun nextWalksThePagesThenNotNowFinishes() =
        runTest(main.dispatcher) {
            val viewModel = OnboardingViewModel(FakeSources())
            runCurrent()
            viewModel.refresh(notificationsAllowed = false)
            viewModel.next()
            viewModel.next()
            assertEquals(OnboardingStep.ThisPhone, viewModel.state.value.step)
            viewModel.next()
            assertEquals(OnboardingStep.SignIn, viewModel.state.value.step)
            viewModel.notNow()
            assertEquals(OnboardingStep.Notifications, viewModel.state.value.step)
            assertFalse(viewModel.state.value.finished)
            viewModel.next()
            assertTrue(viewModel.state.value.finished)
        }

    @Test
    fun skipGoesStraightToSignIn() =
        runTest(main.dispatcher) {
            val viewModel = OnboardingViewModel(FakeSources())
            runCurrent()
            viewModel.skipIntro()
            assertEquals(OnboardingStep.SignIn, viewModel.state.value.step)
        }

    @Test
    fun signingInOnEbaysPageMovesPastTheSignInStep() =
        runTest(main.dispatcher) {
            val sources = FakeSources()
            val viewModel = OnboardingViewModel(sources)
            runCurrent()
            viewModel.skipIntro()
            assertEquals(OnboardingStep.SignIn, viewModel.state.value.step)
            sources.name.value = "lot.42"
            runCurrent()
            assertEquals(OnboardingStep.Notifications, viewModel.state.value.step)
        }

    @Test
    fun aSessionCookieSeenOnReturnAlsoCounts() =
        runTest(main.dispatcher) {
            val sources = FakeSources()
            val viewModel = OnboardingViewModel(sources)
            runCurrent()
            viewModel.skipIntro()
            sources.cookie = true
            viewModel.refresh(notificationsAllowed = true)
            assertTrue("signed in and notifications allowed: nothing left", viewModel.state.value.finished)
        }

    @Test
    fun allowedNotificationsEndOnboardingWhenTheStepIsReached() =
        runTest(main.dispatcher) {
            val sources = FakeSources().apply { cookie = true }
            val viewModel = OnboardingViewModel(sources)
            runCurrent()
            viewModel.refresh(notificationsAllowed = true)
            viewModel.skipIntro()
            assertTrue(viewModel.state.value.finished)
        }

    @Test
    fun backGoesOneStepBackAndLeavesFromTheFirstPage() =
        runTest(main.dispatcher) {
            val viewModel = OnboardingViewModel(FakeSources())
            runCurrent()
            assertFalse(viewModel.back())
            viewModel.skipIntro()
            assertTrue(viewModel.back())
            assertEquals(OnboardingStep.ThisPhone, viewModel.state.value.step)
            viewModel.back()
            viewModel.back()
            assertEquals(OnboardingStep.LastSeconds, viewModel.state.value.step)
        }
}
