package app.kestrel.ui.screens.handoff

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kestrel.ui.screens.signinbrowser.EbayPage
import app.kestrel.ui.screens.signinbrowser.EbayPageController
import app.kestrel.ui.screens.signinbrowser.EbayPageListener
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.ShellMessage
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.shell.show

/**
 * A visible browser on snipe [id]'s listing, for a captcha or a verification Kestrel hands to the user:
 * Destination.HandOff. Done re-runs the dry run and goes back with a message; close goes back without it. Back
 * goes back through eBay's pages first.
 */
@Composable
fun HandOffDestination(
    id: Long,
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    viewModel: HandOffViewModel = handOffViewModel(id),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val controller = remember { EbayPageController() }
    LaunchedEffect(navigator) { navigator.messages.collect { snackbarHostState.show(it) } }
    BackHandler { if (controller.canGoBack()) controller.goBack() else navigator.back() }
    val listener =
        remember(viewModel) {
            object : EbayPageListener {
                override fun onProgress(
                    progress: Int,
                    url: String?,
                ) = viewModel.onProgress(progress, url)

                override fun onPageFinished(
                    url: String,
                    title: String,
                ) = viewModel.onPageFinished(url)

                override fun onThirdPartySignIn(url: String) = viewModel.onThirdPartySignIn(url)

                override fun onRendererGone(crashed: Boolean) = viewModel.onRendererGone()
            }
        }
    HandOffScreen(
        state = state,
        onClose = navigator::back,
        onDone = {
            viewModel.done()?.let { navigator.leaveMessage(ShellMessage(it)) }
            navigator.back()
        },
        onSheetAction = viewModel::onSheetAction,
        onSheetDismiss = viewModel::onSheetDismissed,
        modifier = modifier,
        windowInsets = windowInsets,
        snackbarHostState = snackbarHostState,
        page = { pageModifier ->
            state.url?.let { url ->
                EbayPage(
                    url = url,
                    generation = state.page.generation,
                    controller = controller,
                    listener = listener,
                    modifier = pageModifier,
                )
            }
        },
    )
}

@Composable
private fun handOffViewModel(id: Long): HandOffViewModel {
    val context = LocalContext.current.applicationContext
    return kestrelViewModel(key = "hand-off-$id") { container -> HandOffViewModel.from(id, container, context) }
}
