package app.kestrel.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kestrel.AppContainer
import app.kestrel.KestrelApp

/**
 * A ViewModel built from the app's [AppContainer], scoped to the screen's back-stack entry (the shell installs
 * Navigation 3's ViewModel store decorator), so two Lot screens never share one. Use it as the default value of the
 * destination's ViewModel parameter:
 *
 * ```
 * @Composable
 * fun LotDestination(
 *     id: Long,
 *     navigator: KestrelNavigator,
 *     modifier: Modifier = Modifier,
 *     windowInsets: WindowInsets = WindowInsets.safeDrawing,
 *     viewModel: LotViewModel = kestrelViewModel(key = "lot-$id") { LotViewModel(id, it) },
 * )
 * ```
 */
@Composable
inline fun <reified T : ViewModel> kestrelViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> T,
): T {
    val container = (LocalContext.current.applicationContext as KestrelApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
