package app.kestrel.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** The dim behind a dialog: the 32 % black of the sheets' scrim (DESIGN 2.2 and 3.16), not the platform's 60 %. */
private const val DIALOG_DIM = 0.32f

/**
 * Kestrel's one dialog (the sign-out confirmation and the max spelt out on S2): surfaceContainerLow like the sheets,
 * 24 dp from each side of the screen whatever window the dialog gets, and the page dimmed as the sheets dim it.
 */
@Composable
fun KestrelDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = {
            DialogDim()
            title()
        },
        text = text,
        modifier = modifier.padding(horizontal = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

/** Sets the dim of the dialog window this is composed in. */
@Composable
private fun DialogDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(DIALOG_DIM) }
}
