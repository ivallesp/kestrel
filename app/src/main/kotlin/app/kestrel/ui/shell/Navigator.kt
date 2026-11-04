package app.kestrel.ui.shell

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A message one screen leaves for the next screen with a snackbar, such as "Armed: bids Thu 13 Nov at 20:04:12" after
 * Add closes. Undo messages stay [UNDO_MILLIS] (DESIGN 3.20). [onAction] runs when the user taps [actionLabel].
 */
@Immutable
class ShellMessage(
    val text: String,
    val actionLabel: String? = null,
    val durationMillis: Long = SHORT_MILLIS,
    val onAction: (() -> Unit)? = null,
) {
    companion object {
        const val SHORT_MILLIS = 4_000L
        const val UNDO_MILLIS = 10_000L
    }
}

/** Shows [message] for its own duration and runs its action if the user taps it. */
suspend fun SnackbarHostState.show(message: ShellMessage) {
    val result =
        withTimeoutOrNull(message.durationMillis) {
            showSnackbar(message.text, actionLabel = message.actionLabel, duration = SnackbarDuration.Indefinite)
        }
    if (result == null) currentSnackbarData?.dismiss()
    if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
}

/**
 * How screens move between each other. The shell owns the back stack; screens only call these.
 *
 * - [open] pushes a screen; [back] pops one (and leaves the app from the last one).
 * - [replace] swaps the top screen, for example Lot to Result when the result arrives, or SignIn to SignInBrowser.
 * - [resetTo] makes [Destination] the only screen.
 * - [completeOnboarding] stores that onboarding is done and opens Home (then the link the app was opened with, if any).
 * - [leaveMessage] queues a snackbar for whichever screen collects [messages] next (Home after Add, for example).
 */
@Stable
interface KestrelNavigator {
    val current: Destination

    fun open(destination: Destination)

    fun replace(destination: Destination)

    fun back()

    fun resetTo(destination: Destination)

    fun completeOnboarding()

    fun leaveMessage(message: ShellMessage)

    val messages: Flow<ShellMessage>
}

/** The navigator over a Navigation 3 back stack. */
internal class BackStackNavigator(
    private val backStack: MutableList<NavKey>,
    private val exit: () -> Unit,
    private val onboardingDone: () -> Unit,
    /** Where to go after onboarding: the screen a share or a notification asked for on the first launch. */
    private var afterOnboarding: Destination? = null,
) : KestrelNavigator {
    private val pending = Channel<ShellMessage>(Channel.BUFFERED)

    override val messages: Flow<ShellMessage> = pending.receiveAsFlow()

    override val current: Destination get() = backStack.lastOrNull() as? Destination ?: Destination.Home

    override fun open(destination: Destination) {
        if (current != destination) backStack.add(destination)
    }

    override fun replace(destination: Destination) {
        if (backStack.isEmpty()) backStack.add(destination) else backStack[backStack.lastIndex] = destination
    }

    override fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else exit()
    }

    override fun resetTo(destination: Destination) {
        // Add first so the stack is never empty, which Navigation 3 does not allow.
        backStack.add(destination)
        while (backStack.size > 1) backStack.removeAt(0)
    }

    override fun completeOnboarding() {
        onboardingDone()
        resetTo(Destination.Home)
        afterOnboarding?.let { open(it) }
        afterOnboarding = null
    }

    override fun leaveMessage(message: ShellMessage) {
        pending.trySend(message)
    }

    /** A screen asked for from outside the app (a share, a notification) while it runs. */
    fun openFromOutside(destination: Destination) {
        if (current == Destination.Onboarding) {
            afterOnboarding = destination
            return
        }
        open(destination)
    }
}
