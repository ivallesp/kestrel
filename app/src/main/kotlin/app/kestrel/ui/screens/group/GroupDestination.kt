package app.kestrel.ui.screens.group

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.screens.add.EditMaxMode
import app.kestrel.ui.screens.add.EditMaxSheet
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.ShellMessage
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.shell.show

/**
 * One "win 1 of N" group: Destination.Group. Collects [GroupViewModel] and draws the stateless [GroupScreen], with
 * the edit sheet for a row's "Set a max" or "Raise max" and the picker for "Add a snipe to this group".
 */
@Composable
fun GroupDestination(
    id: Long,
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    viewModel: GroupViewModel =
        kestrelViewModel(
            key = "group-$id",
        ) { container -> GroupViewModel(id, ContainerGroupServices(container, container.context)) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var picking by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingMode by rememberSaveable { mutableStateOf<EditMaxMode?>(null) }

    LaunchedEffect(navigator) { navigator.messages.collect { snackbarHostState.show(it) } }
    LaunchedEffect(viewModel, navigator) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupEvent.Ungrouped -> {
                    navigator.leaveMessage(event.message)
                    navigator.back()
                }

                is GroupEvent.Message -> {
                    snackbarHostState.show(ShellMessage(event.text))
                }
            }
        }
    }
    val actions =
        remember(viewModel, navigator) {
            object : GroupActions {
                override fun back() = navigator.back()

                override fun open(row: GroupLotRowState) {
                    navigator.open(
                        if (row.status.kind.isFinal) Destination.Result(row.snipeId) else Destination.Lot(row.snipeId),
                    )
                }

                override fun act(
                    snipeId: Long,
                    action: LotAction,
                ) {
                    when (action) {
                        LotAction.SET_MAX, LotAction.RAISE_MAX, LotAction.EDIT_MAX -> {
                            editingMode =
                                when (action) {
                                    LotAction.SET_MAX -> EditMaxMode.Set
                                    LotAction.RAISE_MAX -> EditMaxMode.Raise
                                    else -> EditMaxMode.Edit
                                }
                            editingId = snipeId
                        }

                        LotAction.SIGN_IN -> {
                            navigator.open(Destination.SignIn)
                        }

                        LotAction.OPEN_EBAY_PAGE, LotAction.OPEN_ON_EBAY -> {
                            navigator.open(Destination.HandOff(snipeId))
                        }

                        LotAction.FIX -> {
                            navigator.open(Destination.Ready)
                        }

                        LotAction.VIEW_RECEIPT -> {
                            navigator.open(Destination.Result(snipeId))
                        }

                        // The data layer has no un-cancel yet, so rows never offer it.
                        LotAction.UNDO_CANCEL -> {}
                    }
                }

                override fun ungroup() = viewModel.ungroup()

                override fun addSnipe() {
                    picking = true
                }
            }
        }
    Box(modifier) {
        GroupScreen(
            state = state,
            actions = actions,
            windowInsets = windowInsets,
            snackbarHostState = snackbarHostState,
        )
        if (picking) {
            SnipePickerSheet(
                groupName = state.title,
                candidates = state.candidates,
                now = state.now,
                onPick = { snipeId ->
                    picking = false
                    viewModel.add(snipeId)
                },
                onDismiss = { picking = false },
            )
        }
        editingId?.let { snipeId ->
            EditMaxSheet(
                snipeId = snipeId,
                navigator = navigator,
                onDismiss = {
                    editingId = null
                    editingMode = null
                },
                mode = editingMode,
            )
        }
    }
}
