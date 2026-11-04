package app.kestrel.ui.screens.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.AppContainer
import app.kestrel.model.EbaySite
import app.kestrel.web.EbaySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The first launch: three short pages, then sign-in (S6) and the notifications permission. */
enum class OnboardingStep {
    /** What Kestrel does: bids in the last seconds. */
    LastSeconds,

    /** The max is a hard ceiling. */
    YourMax,

    /** It bids from this phone. */
    ThisPhone,

    /** S6, inside onboarding. */
    SignIn,

    /** The notifications permission, asked for in context. */
    Notifications,
    ;

    val isIntro: Boolean get() = this == LastSeconds || this == YourMax || this == ThisPhone

    companion object {
        val intro: List<OnboardingStep> = listOf(LastSeconds, YourMax, ThisPhone)
    }
}

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep,
    /** Set once the last step is done: the screen then finishes onboarding and opens Ready. */
    val finished: Boolean,
)

/** What onboarding reads, behind one seam so the ViewModel runs on fakes in tests. */
interface OnboardingSources {
    /** The stored eBay name; null while signed out. */
    fun signedInName(): Flow<String?>

    /** True when the WebView cookie store holds a signed-in eBay UK session. */
    fun hasSessionCookie(): Boolean
}

/**
 * Walks the first launch. The sign-in step is skipped when the phone is already signed in, and passed as soon as it
 * is (on the way back from eBay's page); the notifications step is skipped when they are already allowed.
 */
class OnboardingViewModel(
    private val sources: OnboardingSources,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingUiState(OnboardingStep.LastSeconds, finished = false))
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    private var signedIn = false
    private var notificationsAllowed = false

    init {
        viewModelScope.launch {
            sources.signedInName().collect { name ->
                signedIn = name != null || sources.hasSessionCookie()
                if (signedIn && mutableState.value.step == OnboardingStep.SignIn) advance()
            }
        }
    }

    /** "Next" on the pages, and the permission's answer on the last step. */
    fun next() = advance()

    /** "Skip" on the pages: straight to the sign-in step (or past it when already signed in). */
    fun skipIntro() {
        mutableState.update { it.copy(step = OnboardingStep.ThisPhone) }
        advance()
    }

    /** "Not now" on the sign-in and notifications steps. */
    fun notNow() = advance()

    /** System back: one step back. False on the first page, where back leaves the app. */
    fun back(): Boolean {
        val current = mutableState.value.step
        val previous =
            when (current) {
                OnboardingStep.LastSeconds -> return false
                OnboardingStep.YourMax -> OnboardingStep.LastSeconds
                OnboardingStep.ThisPhone -> OnboardingStep.YourMax
                OnboardingStep.SignIn -> OnboardingStep.ThisPhone
                OnboardingStep.Notifications -> if (signedIn) OnboardingStep.ThisPhone else OnboardingStep.SignIn
            }
        mutableState.update { it.copy(step = previous) }
        return true
    }

    /** The screen is shown again (back from eBay's page) or the permission changed. */
    fun refresh(notificationsAllowed: Boolean) {
        this.notificationsAllowed = notificationsAllowed
        signedIn = signedIn || sources.hasSessionCookie()
        val stepDone =
            when (mutableState.value.step) {
                OnboardingStep.SignIn -> signedIn
                OnboardingStep.Notifications -> notificationsAllowed
                else -> false
            }
        if (stepDone) advance()
    }

    private fun advance() {
        mutableState.update { current ->
            val next = OnboardingSteps.after(current.step, signedIn, notificationsAllowed)
            if (next == null) current.copy(finished = true) else current.copy(step = next)
        }
    }

    companion object {
        fun from(container: AppContainer): OnboardingViewModel =
            OnboardingViewModel(ContainerOnboardingSources(container))
    }
}

/** The order of the steps, with the ones already done skipped. */
object OnboardingSteps {
    /** The step after [step], or null when onboarding is over. */
    fun after(
        step: OnboardingStep,
        signedIn: Boolean,
        notificationsAllowed: Boolean,
    ): OnboardingStep? =
        when (step) {
            OnboardingStep.LastSeconds -> {
                OnboardingStep.YourMax
            }

            OnboardingStep.YourMax -> {
                OnboardingStep.ThisPhone
            }

            OnboardingStep.ThisPhone -> {
                when {
                    !signedIn -> OnboardingStep.SignIn
                    !notificationsAllowed -> OnboardingStep.Notifications
                    else -> null
                }
            }

            OnboardingStep.SignIn -> {
                if (notificationsAllowed) null else OnboardingStep.Notifications
            }

            OnboardingStep.Notifications -> {
                null
            }
        }
}

internal class ContainerOnboardingSources(
    private val container: AppContainer,
) : OnboardingSources {
    override fun signedInName(): Flow<String?> =
        container.settings.settings
            .map { it.signedInName }
            .distinctUntilChanged()

    override fun hasSessionCookie(): Boolean = EbaySession.hasCookies(EbaySite.UK.host)
}
