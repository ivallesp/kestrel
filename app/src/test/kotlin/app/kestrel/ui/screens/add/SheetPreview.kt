package app.kestrel.ui.screens.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.kestrel.ui.components.SheetDragHandle
import app.kestrel.ui.components.SheetLayout
import app.kestrel.ui.harness.GestureBarHeight
import app.kestrel.ui.harness.StatusBarHeight
import app.kestrel.ui.theme.SheetShape

/**
 * A bottom sheet as the phone draws it, without a window: [backdrop] under a 32 % black scrim, then the sheet in
 * surfaceContainerLow with its handle and [SheetLayout], above the gesture handle and below the status bar.
 */
@Composable
fun SheetPreview(
    title: String,
    modifier: Modifier = Modifier,
    backdrop: @Composable () -> Unit = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) },
    body: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        backdrop()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
        Box(Modifier.fillMaxSize().padding(top = StatusBarHeight)) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(SheetShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SheetDragHandle()
                SheetLayout(title = title, windowInsets = WindowInsets(bottom = GestureBarHeight)) { body() }
            }
        }
    }
}
