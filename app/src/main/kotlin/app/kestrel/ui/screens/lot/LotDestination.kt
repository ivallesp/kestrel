package app.kestrel.ui.screens.lot

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.shell.PlaceholderScreen

/**
 * S3, one snipe's detail and final minute: Destination.Lot.
 *
 * Placeholder: the Lot detail (S3) screen replaces this body with its ViewModel and stateless screen.
 * Keep the signature: the app shell calls it.
 */
@Composable
fun LotDestination(
    id: Long,
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    PlaceholderScreen(
        overline = "S3 · Lot detail",
        title = "Lot detail",
        detail = "Snipe $id.",
        modifier = modifier,
        windowInsets = windowInsets,
        onBack = navigator::back,
    )
}
