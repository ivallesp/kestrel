package app.kestrel.ui.harness

import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.ShellMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A navigator for tests: it moves nowhere and records what a screen asked for. */
class RecordingNavigator(
    override val current: Destination = Destination.Home,
) : KestrelNavigator {
    val opened = mutableListOf<Destination>()
    val replaced = mutableListOf<Destination>()
    val messagesLeft = mutableListOf<ShellMessage>()
    var backs = 0
        private set
    var onboardingCompleted = false
        private set

    override val messages: Flow<ShellMessage> = emptyFlow()

    override fun open(destination: Destination) {
        opened += destination
    }

    override fun replace(destination: Destination) {
        replaced += destination
    }

    override fun back() {
        backs++
    }

    override fun resetTo(destination: Destination) {
        replaced += destination
    }

    override fun completeOnboarding() {
        onboardingCompleted = true
    }

    override fun leaveMessage(message: ShellMessage) {
        messagesLeft += message
    }
}
