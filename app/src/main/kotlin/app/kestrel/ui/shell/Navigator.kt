package app.kestrel.ui.shell

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
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
 * - [leaveMessage] queues a snackbar for the screen on top after the navigation that follows it: Home when Add calls
 *   it and then [back], or the screen under a sheet when no navigation follows. Call it right before that
 *   navigation, in the same callback. A message no screen took by the navigation after that one is dropped.
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

/**
 * The navigator over a Navigation 3 back stack. The shell gives each screen [forEntry], whose messages wait until
 * that screen is on top: a screen that leaves a message and goes back never takes its own message, even while its
 * exit animation still runs.
 */
internal class BackStackNavigator(
    private val backStack: MutableList<NavKey>,
    private val exit: () -> Unit,
    private val onboardingDone: () -> Unit,
    /** Where to go after onboarding: the screen a share or a notification asked for on the first launch. */
    private var afterOnboarding: Destination? = null,
) : KestrelNavigator {
    /** A message no screen has taken yet, and the back stack's version when it was left. */
    private class Waiting(
        val message: ShellMessage,
        val leftAt: Int,
    )

    /** Messages no screen has taken yet, oldest first. */
    private val waiting = MutableStateFlow<List<Waiting>>(emptyList())

    /** Bumped on every change to the back stack, so a waiting screen looks again once it is on top. */
    private val stackVersion = MutableStateFlow(0)

    /** Every message, for whichever screen collects it; screens in the shell use [forEntry] instead. */
    override val messages: Flow<ShellMessage> = messagesFor(entry = null)

    override val current: Destination get() = backStack.lastOrNull() as? Destination ?: Destination.Home

    override fun open(destination: Destination) {
        if (current != destination) changeStack { backStack.add(destination) }
    }

    override fun replace(destination: Destination) {
        changeStack {
            if (backStack.isEmpty()) backStack.add(destination) else backStack[backStack.lastIndex] = destination
        }
    }

    override fun back() {
        if (backStack.size > 1) changeStack { backStack.removeAt(backStack.lastIndex) } else exit()
    }

    override fun resetTo(destination: Destination) {
        changeStack {
            // Add first so the stack is never empty, which Navigation 3 does not allow.
            backStack.add(destination)
            while (backStack.size > 1) backStack.removeAt(0)
        }
    }

    override fun completeOnboarding() {
        onboardingDone()
        resetTo(Destination.Home)
        afterOnboarding?.let { open(it) }
        afterOnboarding = null
    }

    override fun leaveMessage(message: ShellMessage) {
        waiting.update { it + Waiting(message, leftAt = stackVersion.value) }
    }

    /** The navigator for the screen of back-stack [entry]: the same moves, and only the messages it may take. */
    fun forEntry(entry: Destination): KestrelNavigator = EntryNavigator(this, messagesFor(entry))

    /**
     * Messages for the screen of [entry] (null: any screen), each taken once. A message is taken only while [entry]
     * is on top, judged when the screen's collector runs, which is after the navigation that followed [leaveMessage]
     * (screens collect on the main thread, so they never run between the two calls).
     */
    private fun messagesFor(entry: NavKey?): Flow<ShellMessage> =
        flow {
            while (true) {
                emit(combine(waiting, stackVersion) { _, _ -> }.mapNotNull { take(entry) }.first())
            }
        }

    private fun take(entry: NavKey?): ShellMessage? {
        if (entry != null && backStack.lastOrNull() != entry) return null
        while (true) {
            val queued = waiting.value
            val version = stackVersion.value
            // A message is for the screen on top after the one navigation that follows it; a later one drops it.
            val fresh = queued.filter { version - it.leftAt <= 1 }
            val taken = fresh.firstOrNull()
            if (waiting.compareAndSet(queued, fresh.drop(1))) return taken?.message
        }
    }

    private inline fun changeStack(change: () -> Unit) {
        change()
        stackVersion.update { it + 1 }
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

/** One screen's view of the shell's navigator: every move is the shell's, [messages] are the screen's own. */
private class EntryNavigator(
    private val shell: BackStackNavigator,
    override val messages: Flow<ShellMessage>,
) : KestrelNavigator by shell
