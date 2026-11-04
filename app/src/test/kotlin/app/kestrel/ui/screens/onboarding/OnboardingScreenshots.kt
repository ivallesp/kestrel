package app.kestrel.ui.screens.onboarding

import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.harness.ScreenshotTest
import org.junit.Test

/** The first launch, page by page: three pages on what Kestrel does, S6 inside onboarding, then notifications. */
class OnboardingScreenshots : ScreenshotTest() {
    private fun page(
        name: String,
        step: OnboardingStep,
        darkTheme: Boolean,
        fontScale: Float = 1f,
    ) = capture(name, darkTheme = darkTheme, time = "18:00", fontScale = fontScale) {
        OnboardingScreen(OnboardingUiState(step, finished = false), NoOnboardingActions, windowInsets = PhoneInsets)
    }

    @Test
    fun lastSecondsLight() = page("onboarding-1", OnboardingStep.LastSeconds, darkTheme = false)

    @Test
    fun lastSecondsDark() = page("onboarding-1", OnboardingStep.LastSeconds, darkTheme = true)

    @Test
    fun yourMaxLight() = page("onboarding-2", OnboardingStep.YourMax, darkTheme = false)

    @Test
    fun thisPhoneLight() = page("onboarding-3", OnboardingStep.ThisPhone, darkTheme = false)

    @Test
    fun thisPhoneDark() = page("onboarding-3", OnboardingStep.ThisPhone, darkTheme = true)

    @Test
    fun thisPhoneLargeText() = page("onboarding-3", OnboardingStep.ThisPhone, darkTheme = false, fontScale = 1.3f)

    @Test
    fun signInLight() = page("onboarding-signin", OnboardingStep.SignIn, darkTheme = false)

    @Test
    fun notificationsLight() = page("onboarding-notifications", OnboardingStep.Notifications, darkTheme = false)

    @Test
    fun notificationsDark() = page("onboarding-notifications", OnboardingStep.Notifications, darkTheme = true)
}

/** Onboarding's actions for screenshots: none of them goes anywhere. */
internal object NoOnboardingActions : OnboardingActions {
    override fun next() = Unit

    override fun skip() = Unit

    override fun continueToEbay() = Unit

    override fun notNow() = Unit

    override fun back() = Unit

    override fun allowNotifications() = Unit
}
