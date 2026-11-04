package app.kestrel.ui.screens.result

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kestrel.ui.screens.lot.LotEffect
import app.kestrel.ui.screens.lot.LotPage
import app.kestrel.ui.screens.lot.LotViewModel
import app.kestrel.ui.screens.lot.NotOnTheList
import app.kestrel.ui.screens.lot.routeAction
import app.kestrel.ui.screens.lot.shareReceipt
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.shell.show
import kotlinx.coroutines.launch

/**
 * S4, one snipe's result and receipt: Destination.Result. It reads the snipe through the lot's ViewModel; a snipe
 * that is still running opens its live detail instead.
 */
@Composable
fun ResultDestination(
    id: Long,
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    viewModel: LotViewModel = kestrelViewModel(key = "result-$id") { LotViewModel(id, it) },
) {
    val page by viewModel.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(navigator) { navigator.messages.collect { snackbars.show(it) } }
    LaunchedEffect(viewModel, navigator) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LotEffect.Show -> {
                    launch { snackbars.show(effect.message) }
                }

                is LotEffect.LeaveAndClose -> {
                    navigator.leaveMessage(effect.message)
                    navigator.back()
                }
            }
        }
    }
    val live = page is LotPage.Live
    LaunchedEffect(live) { if (live) navigator.replace(Destination.Lot(id)) }
    Box(modifier.fillMaxSize()) {
        when (val current = page) {
            is LotPage.Finished -> {
                ResultScreen(
                    state = current.state,
                    windowInsets = windowInsets,
                    onClose = navigator::back,
                    onShare = { shareReceipt(context, current.state.shareText) },
                    onOpenOnEbay = { navigator.open(Destination.HandOff(id)) },
                    onDelete = viewModel::delete,
                    onAction = { action -> routeAction(action, null, id, navigator, openSheet = {}) },
                )
            }

            LotPage.Gone -> {
                NotOnTheList(onBack = navigator::back, windowInsets = windowInsets)
            }

            LotPage.Loading, is LotPage.Live -> {
                NotOnTheList(onBack = navigator::back, windowInsets = windowInsets, showWords = false)
            }
        }
        SnackbarHost(
            snackbars,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(8.dp),
        )
    }
}
