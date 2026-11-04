package app.kestrel.ui.screens.ready

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.ShellMessage
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.shell.show
import kotlinx.coroutines.launch

/**
 * S5, the readiness checks: Destination.Ready. The checks run each time the screen resumes, which covers the first
 * open and every return from a system page a Fix opened.
 */
@Composable
fun ReadyDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    viewModel: ReadyViewModel = readyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(navigator) { navigator.messages.collect { snackbarHostState.show(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.checkAgain() }
    ReadyScreen(
        state = state,
        onBack = navigator::back,
        onCheckAgain = viewModel::checkAgain,
        onAction = { action ->
            when (action) {
                is CheckAction.SystemPage -> {
                    if (!context.openSystemPage(viewModel.fixIntent(action.key))) {
                        scope.launch { snackbarHostState.show(ShellMessage(NO_SETTINGS_PAGE)) }
                    }
                }

                CheckAction.SignIn -> {
                    navigator.open(Destination.SignIn)
                }

                is CheckAction.OpenSnipe -> {
                    navigator.open(Destination.Lot(action.snipeId))
                }
            }
        },
        modifier = modifier,
        windowInsets = windowInsets,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun readyViewModel(): ReadyViewModel {
    val context = LocalContext.current.applicationContext
    return kestrelViewModel(key = "ready") { container -> ReadyViewModel.from(container, context) }
}

/**
 * Opens a system settings page, falling back to Kestrel's own app page when this phone has no screen for [intent].
 * False when neither opens.
 */
internal fun Context.openSystemPage(intent: Intent?): Boolean {
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    for (candidate in listOfNotNull(intent, fallback)) {
        try {
            startActivity(candidate)
            return true
        } catch (error: ActivityNotFoundException) {
            continue
        } catch (error: SecurityException) {
            continue
        }
    }
    return false
}

private const val NO_SETTINGS_PAGE = "This phone has no settings page for that check."
