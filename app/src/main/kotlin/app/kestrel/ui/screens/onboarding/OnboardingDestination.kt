package app.kestrel.ui.screens.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.kestrelViewModel

/**
 * The first launch: Destination.Onboarding. It ends with navigator.completeOnboarding(), which stores the flag and
 * opens Home (and anything a share asked for), then opens Ready to snipe so the phone's checks come last.
 */
@Composable
fun OnboardingDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    viewModel: OnboardingViewModel = kestrelViewModel(key = "onboarding") { OnboardingViewModel.from(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Allowed or not, the question was asked in context once; Ready to snipe shows it if it is still off.
            viewModel.next()
        }
    // Every return to this screen (from eBay's sign-in page, for one) checks what is already done.
    LaunchedEffect(viewModel) { viewModel.refresh(context.notificationsAllowed()) }
    LaunchedEffect(state.finished) {
        if (state.finished) {
            navigator.completeOnboarding()
            navigator.open(Destination.Ready)
        }
    }
    BackHandler(enabled = state.step != OnboardingStep.LastSeconds) { viewModel.back() }
    val actions =
        remember(viewModel, navigator, permission) {
            object : OnboardingActions {
                override fun next() = viewModel.next()

                override fun skip() = viewModel.skipIntro()

                override fun continueToEbay() = navigator.open(Destination.SignInBrowser)

                override fun notNow() = viewModel.notNow()

                override fun back() {
                    viewModel.back()
                }

                override fun allowNotifications() = permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    OnboardingScreen(state = state, actions = actions, modifier = modifier, windowInsets = windowInsets)
}

private fun Context.notificationsAllowed(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
