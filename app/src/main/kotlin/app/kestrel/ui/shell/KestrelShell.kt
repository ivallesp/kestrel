package app.kestrel.ui.shell

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import app.kestrel.ui.components.LocalPlateTransition
import app.kestrel.ui.components.PlateTransition
import app.kestrel.ui.screens.add.AddDestination
import app.kestrel.ui.screens.group.GroupDestination
import app.kestrel.ui.screens.handoff.HandOffDestination
import app.kestrel.ui.screens.history.HistoryDestination
import app.kestrel.ui.screens.home.HomeDestination
import app.kestrel.ui.screens.lot.LotDestination
import app.kestrel.ui.screens.onboarding.OnboardingDestination
import app.kestrel.ui.screens.ready.ReadyDestination
import app.kestrel.ui.screens.result.ResultDestination
import app.kestrel.ui.screens.settings.SettingsDestination
import app.kestrel.ui.screens.signin.SignInDestination
import app.kestrel.ui.screens.signinbrowser.SignInBrowserDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The app: one Navigation 3 NavDisplay over [Destination] keys, inside a SharedTransitionLayout so a lot's plate
 * travels from the list to its detail. Each entry keeps its own saved state and ViewModels. [startStack] is the back
 * stack of a fresh start (a restored one wins); [incoming] carries screens asked for while the app runs (a share, a
 * notification). [onExit] leaves the app from the last screen.
 */
@Composable
fun KestrelShell(
    startStack: List<Destination>,
    onFinishOnboarding: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    incoming: Flow<Destination> = emptyFlow(),
    afterOnboarding: Destination? = null,
) {
    val backStack = rememberNavBackStack(*startStack.toTypedArray())
    val navigator = remember(backStack) { BackStackNavigator(backStack, onExit, onFinishOnboarding, afterOnboarding) }
    LaunchedEffect(navigator, incoming) { incoming.collect { navigator.openFromOutside(it) } }
    val transitions = rememberScreenTransitions()
    SharedTransitionLayout(modifier) {
        val sharedScope = this
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.back() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            sharedTransitionScope = sharedScope,
            transitionSpec = transitions.push,
            popTransitionSpec = transitions.pop,
            predictivePopTransitionSpec = transitions.predictivePop,
            entryProvider =
                entryProvider {
                    entry<Destination.Home> { Screen(sharedScope) { HomeDestination(navigator) } }
                    entry<Destination.Add> { key -> Screen(sharedScope) { AddDestination(key.link, navigator) } }
                    entry<Destination.Lot> { key -> Screen(sharedScope) { LotDestination(key.id, navigator) } }
                    entry<Destination.Result> { key -> Screen(sharedScope) { ResultDestination(key.id, navigator) } }
                    entry<Destination.Ready> { Screen(sharedScope) { ReadyDestination(navigator) } }
                    entry<Destination.SignIn> { Screen(sharedScope) { SignInDestination(navigator) } }
                    entry<Destination.SignInBrowser> { Screen(sharedScope) { SignInBrowserDestination(navigator) } }
                    entry<Destination.HandOff> { key -> Screen(sharedScope) { HandOffDestination(key.id, navigator) } }
                    entry<Destination.Group> { key -> Screen(sharedScope) { GroupDestination(key.id, navigator) } }
                    entry<Destination.Settings> { Screen(sharedScope) { SettingsDestination(navigator) } }
                    entry<Destination.History> { Screen(sharedScope) { HistoryDestination(navigator) } }
                    entry<Destination.Onboarding> { Screen(sharedScope) { OnboardingDestination(navigator) } }
                },
        )
    }
}

/** Gives a screen the scopes its lot plates need for the shared-element transition. */
@Composable
private fun Screen(
    sharedScope: SharedTransitionScope,
    content: @Composable () -> Unit,
) {
    val animatedScope = LocalNavAnimatedContentScope.current
    val transition = remember(sharedScope, animatedScope) { PlateTransition(sharedScope, animatedScope) }
    CompositionLocalProvider(LocalPlateTransition provides transition, content = content)
}
