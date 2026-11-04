package app.kestrel.ui.screens.signinbrowser

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
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.ShellMessage
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.shell.show

/**
 * S6, eBay's sign-in page in Kestrel's own browser: Destination.SignInBrowser. Back goes back through eBay's pages
 * first. Closing before the sign-in finished leaves "Sign-in didn’t finish." for the screen underneath.
 */
@Composable
fun SignInBrowserDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    viewModel: SignInBrowserViewModel = signInBrowserViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val controller = remember { EbayPageController() }
    LaunchedEffect(navigator) { navigator.messages.collect { snackbarHostState.show(it) } }
    LaunchedEffect(viewModel) { viewModel.notices.collect { snackbarHostState.show(ShellMessage(it)) } }
    val close = {
        if (!viewModel.state.value.signedIn) navigator.leaveMessage(ShellMessage(DID_NOT_FINISH))
        navigator.back()
    }
    BackHandler { if (controller.canGoBack()) controller.goBack() else close() }
    val listener =
        remember(viewModel, controller) {
            object : EbayPageListener {
                override fun onProgress(
                    progress: Int,
                    url: String?,
                ) = viewModel.onProgress(progress, url)

                override fun onPageFinished(
                    url: String,
                    title: String,
                ) {
                    if (viewModel.onPageFinished(url, title)) {
                        controller.evaluate(SignInPages.headerScript) { viewModel.onHeaderRead(url, it) }
                    }
                }

                override fun onNavigationBlocked(
                    url: String,
                    reason: String,
                ) = viewModel.onNavigationBlocked()

                override fun onThirdPartySignIn(url: String) = viewModel.onThirdPartySignIn(url)

                override fun onMainFrameError(url: String) = viewModel.onMainFrameError(url)

                override fun onRendererGone(crashed: Boolean) = viewModel.onRendererGone()
            }
        }
    SignInBrowserScreen(
        state = state,
        onClose = close,
        onSheetAction = { if (viewModel.onSheetAction()) navigator.back() },
        onSheetDismiss = { if (viewModel.onSheetDismissed()) navigator.back() },
        modifier = modifier,
        windowInsets = windowInsets,
        snackbarHostState = snackbarHostState,
        page = { pageModifier ->
            EbayPage(
                url = viewModel.url,
                generation = state.pageGeneration,
                controller = controller,
                listener = listener,
                modifier = pageModifier,
            )
        },
    )
}

@Composable
private fun signInBrowserViewModel(): SignInBrowserViewModel {
    val context = LocalContext.current.applicationContext
    return kestrelViewModel(key = "sign-in-browser") { container -> SignInBrowserViewModel.from(container, context) }
}

private const val DID_NOT_FINISH = "Sign-in didn’t finish."
