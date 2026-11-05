package app.kestrel.ui.screens.lot

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kestrel.ui.model.DryRunCause
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.screens.add.WordsCheckDialog
import app.kestrel.ui.screens.result.ResultScreen
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.shell.show
import app.kestrel.ui.theme.KestrelTheme
import kotlinx.coroutines.launch

/**
 * S3, one snipe's detail and final minute: Destination.Lot. Once the result is known it crossfades in place into S4.
 * [editMax] opens the edit sheet once the lot has loaded (the Live Update's "Edit max", DESIGN 5.1).
 */
@Composable
fun LotDestination(
    id: Long,
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    editMax: Boolean = false,
    viewModel: LotViewModel = kestrelViewModel(key = "lot-$id") { LotViewModel(id, it) },
) {
    val page by viewModel.state.collectAsStateWithLifecycle()
    var editMaxAsked by rememberSaveable { mutableStateOf(editMax) }
    val live = page is LotPage.Live
    LaunchedEffect(live, editMaxAsked) {
        if (live && editMaxAsked) {
            viewModel.openSheet(SheetKind.Edit)
            editMaxAsked = false
        }
    }
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

                is LotEffect.Reopen -> {
                    navigator.leaveMessage(effect.message)
                    navigator.replace(Destination.Lot(effect.snipeId))
                }
            }
        }
    }
    val onAction: (LotAction) -> Unit = { action ->
        val status = (page as? LotPage.Live)?.state?.status
        if (action == LotAction.UNDO_CANCEL) {
            viewModel.undoCancel()
        } else {
            routeAction(action, status, id, navigator, viewModel::openSheet)
        }
    }
    val reduceMotion = KestrelTheme.reduceMotion
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Box(modifier.fillMaxSize()) {
        // The result replaces the live page in place; ticks within a page never crossfade.
        AnimatedContent(
            targetState = page,
            contentKey = { it::class },
            transitionSpec = {
                if (reduceMotion) {
                    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                } else {
                    fadeIn(fade) togetherWith fadeOut(fade)
                }
            },
            label = "lot page",
        ) { current ->
            when (current) {
                LotPage.Loading -> {
                    NotOnTheList(onBack = navigator::back, windowInsets = windowInsets, showWords = false)
                }

                LotPage.Gone -> {
                    NotOnTheList(onBack = navigator::back, windowInsets = windowInsets)
                }

                is LotPage.Live -> {
                    LotScreen(
                        state = current.state,
                        windowInsets = windowInsets,
                        onBack = navigator::back,
                        onAction = onAction,
                        onOpenOnEbay = { navigator.open(Destination.HandOff(id)) },
                        onStopSniping = viewModel::stopSniping,
                        onDelete = viewModel::delete,
                        onTestBidding = viewModel::testBidding,
                    )
                }

                is LotPage.Finished -> {
                    ResultScreen(
                        state = current.state,
                        windowInsets = windowInsets,
                        plateKey = null,
                        onClose = navigator::back,
                        onShare = { shareReceipt(context, current.state.shareText) },
                        onOpenOnEbay = { navigator.open(Destination.HandOff(id)) },
                        onDelete = viewModel::delete,
                        onAction = onAction,
                        onSettle = viewModel::settle,
                    )
                }
            }
        }
        // One polite live region for the whole destination, so the move from S3 to S4 announces the result.
        Box(
            Modifier
                .size(1.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = (page as? LotPage.Finished)?.state?.headline.orEmpty()
                },
        )
        SnackbarHost(
            snackbars,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
    val livePage = (page as? LotPage.Live)?.state
    val sheet = livePage?.sheet
    if (livePage != null && sheet != null) {
        EditMaxSheet(
            state = sheet,
            input = viewModel.maxInput,
            now = livePage.now,
            onSave = viewModel::save,
            onDismiss = viewModel::closeSheet,
            onUseAmount = viewModel::useAmount,
            onSelectLead = viewModel::selectLead,
        )
        sheet.wordsCheck?.let { check ->
            WordsCheckDialog(check, onConfirm = viewModel::confirmWords, onDismiss = viewModel::dismissWords)
        }
    }
}

/** Where each status action leads: a sheet for the max, sign-in, or the listing in Kestrel's own browser. */
internal fun routeAction(
    action: LotAction,
    status: LotStatus?,
    id: Long,
    navigator: KestrelNavigator,
    openSheet: (SheetKind) -> Unit,
) {
    when (action) {
        LotAction.SET_MAX -> {
            openSheet(SheetKind.Set)
        }

        LotAction.EDIT_MAX -> {
            openSheet(SheetKind.Edit)
        }

        LotAction.RAISE_MAX -> {
            openSheet(SheetKind.Raise)
        }

        LotAction.SIGN_IN -> {
            navigator.open(Destination.SignIn)
        }

        LotAction.FIX -> {
            val signInPage = status is LotStatus.DryRunFailed && status.cause == DryRunCause.SIGN_IN_PAGE
            navigator.open(if (signInPage) Destination.SignIn else Destination.HandOff(id))
        }

        LotAction.OPEN_EBAY_PAGE, LotAction.OPEN_ON_EBAY -> {
            navigator.open(Destination.HandOff(id))
        }

        LotAction.VIEW_RECEIPT -> {
            navigator.open(Destination.Result(id))
        }

        // The destinations hand "Undo cancel" to the lot's ViewModel before routing; nothing to open here.
        LotAction.UNDO_CANCEL -> {
            Unit
        }
    }
}

/** Shares the text receipt through the system share sheet. */
internal fun shareReceipt(
    context: Context,
    text: String,
) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (error: ActivityNotFoundException) {
        // No app takes text: there is nothing to share it with.
    }
}
