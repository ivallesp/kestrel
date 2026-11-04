package app.kestrel.ui.screens.signinbrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kestrel.model.EbaySite
import app.kestrel.ui.components.KestrelBottomSheet
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.format.noWidow

/**
 * eBay's sign-in page inside Kestrel (DESIGN S6, sign-in page): the host bar with its progress line, the page
 * itself ([page], the visible WebView; screenshots pass a blank stand-in) and the sheets for success and failures.
 */
@Composable
fun SignInBrowserScreen(
    state: SignInBrowserUiState,
    onClose: () -> Unit,
    onSheetAction: () -> Unit,
    onSheetDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    page: @Composable (Modifier) -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
                BrowserBar(host = state.host, progress = state.progress, onClose = onClose)
                page(Modifier.fillMaxWidth().weight(1f))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                            windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        ),
            )
        }
    }
    state.sheet?.let { sheet -> BrowserSheetContent(sheet, onAction = onSheetAction, onDismiss = onSheetDismiss) }
}

/** The words and button of each sheet over an eBay page (the sign-in page and the hand-off share them). */
@Composable
internal fun BrowserSheetContent(
    sheet: BrowserSheet,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val words = sheetWords(sheet)
    // Body copy keeps the paragraph measure; the last two words stay together, so no line holds one word alone.
    val body = MaterialTheme.typography.bodyLarge
    val note = MaterialTheme.typography.bodyMedium
    KestrelBottomSheet(title = words.title, onDismissRequest = onDismiss) {
        Text(words.body.noWidow(), style = body, color = MaterialTheme.colorScheme.onSurface)
        words.note?.let { text ->
            Spacer(Modifier.height(8.dp))
            Text(text.noWidow(), style = note, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(words.action, onClick = onAction)
    }
}

/** The copy of a sheet: [note] is an optional second paragraph in ink soft. */
internal data class SheetWords(
    val title: String,
    val body: String,
    val note: String?,
    val action: String,
)

internal fun sheetWords(sheet: BrowserSheet): SheetWords =
    when (sheet) {
        is BrowserSheet.SignedIn -> {
            SheetWords(
                title = sheet.name?.let { "Signed in as $it" } ?: "Signed in to eBay",
                body = "Kestrel can bid with this account now, from this phone.",
                note = "eBay may ask you to sign in again now and then. Kestrel tells you when it does.",
                action = "Done",
            )
        }

        is BrowserSheet.ThirdParty -> {
            SheetWords(
                title = "Use your eBay password",
                body =
                    "${sheet.provider} sign-in doesn’t work inside apps. Use your eBay email or username and " +
                        "password.",
                note = "No eBay password yet? Set one on ${EbaySite.UK.host.removePrefix("www.")} first.",
                action = "Back to eBay’s page",
            )
        }

        BrowserSheet.Offline -> {
            SheetWords(
                title = "Couldn’t reach eBay",
                body = "Check the connection and try again.",
                note = null,
                action = "Try again",
            )
        }

        BrowserSheet.PageStopped -> {
            SheetWords(
                title = "The page stopped working",
                body = "Android closed eBay’s page. Open it again to carry on.",
                note = null,
                action = "Open it again",
            )
        }
    }
