package app.kestrel.ui.screens.history

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.shell.show

/**
 * Every finished snipe: Destination.History, opened from "See all finished snipes". A row opens its result.
 */
@Composable
fun HistoryDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    viewModel: HistoryViewModel =
        kestrelViewModel(key = "history") { container -> HistoryViewModel(AppHistorySources(container)) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(navigator, snackbarHostState) { navigator.messages.collect { snackbarHostState.show(it) } }
    HistoryScreen(
        state = state,
        onBack = navigator::back,
        onOpenResult = { navigator.open(Destination.Result(it)) },
        modifier = modifier,
        windowInsets = windowInsets,
        snackbarHostState = snackbarHostState,
    )
}
