package app.kestrel.ui.screens.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.ShellMessage
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.shell.show

/**
 * S2, a new snipe: Destination.Add([link] is the shared or pasted text, or null for the link entry).
 *
 * Collects [AddViewModel] and draws the stateless [AddScreen], with the group picker sheet, the amount check and
 * Kestrel's browser for "View listing" over it. After arming it closes and leaves "Armed: bids Thu 13 Nov at
 * 20:04:12" for the list.
 */
@Composable
fun AddDestination(
    link: String?,
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    viewModel: AddViewModel =
        kestrelViewModel(key = "add") { container -> AddViewModel(link, ContainerAddServices(container)) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = remember(context) { ClipboardLinks(context) }
    val canPaste = rememberClipboardHoldsLink(clipboard)
    var browsing by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(navigator) { navigator.messages.collect { snackbarHostState.show(it) } }
    LaunchedEffect(viewModel, navigator) {
        viewModel.events.collect { event ->
            when (event) {
                is AddEvent.Close -> {
                    navigator.leaveMessage(ShellMessage(event.message))
                    navigator.back()
                }

                AddEvent.OpenSignIn -> {
                    navigator.open(Destination.SignIn)
                }

                is AddEvent.OpenSnipe -> {
                    navigator.replace(Destination.Lot(event.snipeId))
                }

                is AddEvent.Message -> {
                    snackbarHostState.show(ShellMessage(event.text))
                }
            }
        }
    }
    // Back from sign-in, a new session lets the snipe arm.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSession()
        onPauseOrDispose {}
    }

    val actions =
        remember(viewModel, navigator, clipboard) {
            object : AddActions {
                override fun close() = navigator.back()

                override fun viewListing(url: String) {
                    browsing = url
                }

                override fun paste() {
                    clipboard.read()?.let(viewModel::paste)
                }

                override fun submitLink() = viewModel.submitLink()

                override fun retry() = viewModel.retry()

                override fun pasteDifferentLink() = viewModel.pasteDifferentLink()

                override fun signIn() = navigator.open(Destination.SignIn)

                override fun openSnipe(snipeId: Long) = navigator.replace(Destination.Lot(snipeId))

                override fun useOddPence() = viewModel.useOddPence()

                override fun selectLead(index: Int) = viewModel.selectLead(index)

                override fun chooseGroup() = viewModel.openGroupPicker()

                override fun arm() = viewModel.arm()

                override fun confirmWords() = viewModel.confirmWords()

                override fun dismissWords() = viewModel.dismissWords()
            }
        }
    Box(modifier) {
        AddScreen(
            state = state,
            actions = actions,
            windowInsets = windowInsets,
            canPaste = canPaste,
            snackbarHostState = snackbarHostState,
        )
        state.groups?.let { picker ->
            GroupPickerSheet(
                state = picker,
                onSelect = viewModel::selectGroup,
                onDone = viewModel::confirmGroup,
                onDismiss = viewModel::dismissGroupPicker,
            )
        }
        browsing?.let { url -> ListingBrowser(url = url, onClose = { browsing = null }, windowInsets = windowInsets) }
    }
}
