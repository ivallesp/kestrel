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
 * travels from the list to its detail. Each entry keeps its own saved state and ViewModels, and gets a navigator whose
 * messages wait until its screen is on top. [startStack] is the back stack of a fresh start (a restored one wins);
 * [incoming] carries screens asked for while the app runs (a share, a notification). [onExit] leaves the app from
 * the last screen.
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
                    entry<Destination.Home> { key -> Screen(sharedScope, navigator, key) { HomeDestination(it) } }
                    entry<Destination.Add> { key ->
                        Screen(sharedScope, navigator, key) { AddDestination(key.link, it) }
                    }
                    entry<Destination.Lot> { key ->
                        Screen(sharedScope, navigator, key) { LotDestination(key.id, it, editMax = key.editMax) }
                    }
                    entry<Destination.Result> { key ->
                        Screen(sharedScope, navigator, key) { ResultDestination(key.id, it) }
                    }
                    entry<Destination.Ready> { key -> Screen(sharedScope, navigator, key) { ReadyDestination(it) } }
                    entry<Destination.SignIn> { key -> Screen(sharedScope, navigator, key) { SignInDestination(it) } }
                    entry<Destination.SignInBrowser> { key ->
                        Screen(sharedScope, navigator, key) { SignInBrowserDestination(it) }
                    }
                    entry<Destination.HandOff> { key ->
                        Screen(sharedScope, navigator, key) { HandOffDestination(key.id, it) }
                    }
                    entry<Destination.Group> { key ->
                        Screen(sharedScope, navigator, key) { GroupDestination(key.id, it) }
                    }
                    entry<Destination.Settings> { key ->
                        Screen(sharedScope, navigator, key) { SettingsDestination(it) }
                    }
                    entry<Destination.History> { key -> Screen(sharedScope, navigator, key) { HistoryDestination(it) } }
                    entry<Destination.Onboarding> { key ->
                        Screen(sharedScope, navigator, key) { OnboardingDestination(it) }
                    }
                },
        )
    }
}

/**
 * Gives the screen of back-stack [entry] the scopes its lot plates need for the shared-element transition, and its
 * own view of the navigator, whose messages wait until this screen is on top.
 */
@Composable
private fun Screen(
    sharedScope: SharedTransitionScope,
    navigator: BackStackNavigator,
    entry: Destination,
    content: @Composable (KestrelNavigator) -> Unit,
) {
    val animatedScope = LocalNavAnimatedContentScope.current
    val transition = remember(sharedScope, animatedScope) { PlateTransition(sharedScope, animatedScope) }
    val entryNavigator = remember(navigator, entry) { navigator.forEntry(entry) }
    CompositionLocalProvider(LocalPlateTransition provides transition) { content(entryNavigator) }
}
