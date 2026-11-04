package app.kestrel.ui.screens.home

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kestrel.ebay.ItemUrlParser
import app.kestrel.model.ParsedLink
import app.kestrel.ui.model.DryRunCause
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.screens.add.EditMaxMode
import app.kestrel.ui.screens.add.EditMaxSheet
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.shell.show

/**
 * S1, the list of snipes: Destination.Home.
 *
 * Collects the ViewModel's state while the screen is visible, shows the navigator's and the ViewModel's snackbars,
 * re-checks the phone whenever the screen comes back (for example from a system page the pill's Fix opened), and
 * turns the screen's actions into navigation. "Raise max" and "Set a max" open their sheet over the list (DESIGN S1);
 * a save leaves its snackbar through the navigator, which this screen collects.
 */
@Composable
fun HomeDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    viewModel: HomeViewModel = kestrelViewModel(key = "home") { container -> HomeViewModel(AppHomeSources(container)) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latestState by rememberUpdatedState(state)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(navigator, snackbarHostState) { navigator.messages.collect { snackbarHostState.show(it) } }
    LaunchedEffect(viewModel, snackbarHostState) { viewModel.messages.collect { snackbarHostState.show(it) } }
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingMode by rememberSaveable { mutableStateOf<EditMaxMode?>(null) }
    val actions =
        remember(navigator, viewModel, context) {
            val edit: (Long, EditMaxMode) -> Unit = { snipeId, mode ->
                editingMode = mode
                editingId = snipeId
            }
            HomeActions(
                onOpenLot = { navigator.open(Destination.Lot(it)) },
                onOpenResult = { navigator.open(Destination.Result(it)) },
                onOpenGroup = { navigator.open(Destination.Group(it)) },
                onOpenSettings = { navigator.open(Destination.Settings) },
                onOpenReady = { navigator.open(Destination.Ready) },
                onReadinessAction = { action ->
                    when (action) {
                        is PillAction.Fix -> {
                            if (!openSystemPage(context, viewModel.fixIntent(action.key))) {
                                navigator.open(Destination.Ready)
                            }
                        }

                        PillAction.Review -> {
                            navigator.open(Destination.Ready)
                        }

                        PillAction.SignIn -> {
                            navigator.open(Destination.SignIn)
                        }
                    }
                },
                onAdd = { navigator.open(Destination.Add(HomeLinks.linkToAdd(clipboardText(context)))) },
                onRefresh = viewModel::refresh,
                onRaiseMax = { edit(it, EditMaxMode.Raise) },
                onQuickRaise = viewModel::quickRaise,
                onStopSniping = viewModel::stopSniping,
                onLotAction = { id, action ->
                    val sheet = HomeLinks.sheetFor(action)
                    if (sheet != null) {
                        edit(id, sheet)
                    } else {
                        navigator.open(HomeLinks.destinationFor(id, action, latestState.lotOf(id)?.status))
                    }
                },
                onSeeAllFinished = { navigator.open(Destination.History) },
            )
        }
    HomeScreen(
        state = state,
        modifier = modifier,
        windowInsets = windowInsets,
        snackbarHostState = snackbarHostState,
        actions = actions,
    )
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

/** Where S1's links and buttons lead. Pure, so each one is tested. */
internal object HomeLinks {
    /**
     * The link Add opens with: an eBay listing (or one of eBay's short links) on the clipboard, else null, which
     * opens Add at its link entry. The clipboard is read only when the user taps Add.
     */
    fun linkToAdd(clipboardText: String?): String? {
        val text = clipboardText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (val parsed = ItemUrlParser.parse(text)) {
            is ParsedLink.Item -> parsed.ref.url
            is ParsedLink.ShortLink -> parsed.url
            is ParsedLink.NotAListing, ParsedLink.NotEbay -> null
        }
    }

    /** The sheet a row's max button opens over the list: "Set a max", "Raise max" or "Edit max"; null for the rest. */
    fun sheetFor(action: LotAction): EditMaxMode? =
        when (action) {
            LotAction.SET_MAX -> EditMaxMode.Set
            LotAction.RAISE_MAX -> EditMaxMode.Raise
            LotAction.EDIT_MAX -> EditMaxMode.Edit
            else -> null
        }

    /**
     * The screen a row's button opens. A dry run that met eBay's sign-in page, like "Sign in", opens the sign-in;
     * eBay's own checks open the page in Kestrel. The max buttons open their sheet ([sheetFor]); should one get here,
     * the lot's detail (S3) has the same sheets.
     */
    fun destinationFor(
        snipeId: Long,
        action: LotAction,
        status: LotStatus?,
    ): Destination =
        when (action) {
            LotAction.SIGN_IN -> {
                Destination.SignIn
            }

            LotAction.OPEN_EBAY_PAGE, LotAction.OPEN_ON_EBAY -> {
                Destination.HandOff(snipeId)
            }

            LotAction.FIX -> {
                val signInPage = (status as? LotStatus.DryRunFailed)?.cause == DryRunCause.SIGN_IN_PAGE
                if (signInPage) Destination.SignIn else Destination.Lot(snipeId)
            }

            LotAction.VIEW_RECEIPT -> {
                Destination.Result(snipeId)
            }

            LotAction.SET_MAX, LotAction.EDIT_MAX, LotAction.RAISE_MAX, LotAction.UNDO_CANCEL -> {
                Destination.Lot(snipeId)
            }
        }
}

private fun HomeUiState.lotOf(snipeId: Long): HomeLot? =
    sections
        .asSequence()
        .flatMap { it.blocks }
        .flatMap { it.lots }
        .firstOrNull { it.id == snipeId }

/** The clipboard's first item as text, read on the user's tap only. */
private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

/** Opens a system settings page; false when there is none to open. */
private fun openSystemPage(
    context: Context,
    intent: Intent?,
): Boolean {
    if (intent == null) return false
    return try {
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        Log.w("HomeDestination", "No settings page for ${intent.action}", error)
        false
    } catch (error: SecurityException) {
        Log.w("HomeDestination", "Not allowed to open ${intent.action}", error)
        false
    }
}
