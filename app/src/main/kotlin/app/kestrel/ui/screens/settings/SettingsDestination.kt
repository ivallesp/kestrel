package app.kestrel.ui.screens.settings

import android.content.Intent
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
import app.kestrel.data.ThemeMode
import app.kestrel.ui.screens.ready.openSystemPage
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.ShellMessage
import app.kestrel.ui.shell.kestrelViewModel
import app.kestrel.ui.shell.show
import kotlinx.coroutines.launch

/** Settings: Destination.Settings. The readiness row is checked again each time the screen resumes. */
@Composable
fun SettingsDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    viewModel: SettingsViewModel = settingsViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(navigator) { navigator.messages.collect { snackbarHostState.show(it) } }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarHostState.show(ShellMessage(it)) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val actions =
        remember(viewModel, navigator, context) {
            object : SettingsActions {
                override fun back() = navigator.back()

                override fun signIn() = navigator.open(Destination.SignIn)

                override fun askToSignOut() = viewModel.askToSignOut()

                override fun confirmSignOut() = viewModel.signOut()

                override fun cancelSignOut() = viewModel.cancelSignOut()

                override fun setDefaultLead(seconds: Int) = viewModel.setDefaultLead(seconds)

                override fun openNotificationSettings() {
                    val intent =
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    if (!context.openSystemPage(intent)) {
                        scope.launch { snackbarHostState.show(ShellMessage(NO_SETTINGS_PAGE)) }
                    }
                }

                override fun openReady() = navigator.open(Destination.Ready)

                override fun setTheme(mode: ThemeMode) = viewModel.setThemeMode(mode)

                override fun setSuggestOddPence(on: Boolean) = viewModel.setSuggestOddPence(on)

                override fun openSheet(sheet: SettingsSheet) = viewModel.openSheet(sheet)

                override fun closeSheet() = viewModel.closeSheet()
            }
        }
    SettingsScreen(
        state = state,
        actions = actions,
        modifier = modifier,
        windowInsets = windowInsets,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun settingsViewModel(): SettingsViewModel {
    val context = LocalContext.current.applicationContext
    return kestrelViewModel(key = "settings") { container -> SettingsViewModel.from(container, context) }
}

private const val NO_SETTINGS_PAGE = "This phone has no notification settings page for Kestrel."
