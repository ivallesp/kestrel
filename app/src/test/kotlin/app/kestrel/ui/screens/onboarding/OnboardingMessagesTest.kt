package app.kestrel.ui.screens.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.ShellMessage
import app.kestrel.ui.theme.KestrelTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Onboarding shows what the screen above it left, such as the sign-in page's "Sign-in didn’t finish." */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class OnboardingMessagesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class Sources : OnboardingSources {
        override fun signedInName(): Flow<String?> = MutableStateFlow(null)

        override fun hasSessionCookie(): Boolean = false
    }

    /** The shell's navigator as onboarding sees it after the sign-in page closed with a message for it. */
    private class LeftAMessage(
        message: ShellMessage,
    ) : KestrelNavigator {
        override val current: Destination = Destination.Onboarding
        override val messages: Flow<ShellMessage> = flowOf(message)

        override fun open(destination: Destination) {}

        override fun replace(destination: Destination) {}

        override fun back() {}

        override fun resetTo(destination: Destination) {}

        override fun completeOnboarding() {}

        override fun leaveMessage(message: ShellMessage) {}
    }

    @Test
    fun closingTheSignInPageBeforeFinishingSaysSoOnTheSignInStep() {
        val viewModel = OnboardingViewModel(Sources())
        viewModel.skipIntro()
        composeRule.setContent {
            KestrelTheme(darkTheme = false, reduceMotion = true) {
                OnboardingDestination(LeftAMessage(ShellMessage("Sign-in didn’t finish.")), viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText("Sign-in didn’t finish.").assertIsDisplayed()
    }
}
