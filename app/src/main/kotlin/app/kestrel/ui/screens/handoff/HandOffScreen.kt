package app.kestrel.ui.screens.handoff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.RollingDigits
import app.kestrel.ui.components.StateLabel
import app.kestrel.ui.format.countdown
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.format.noWidow
import app.kestrel.ui.format.spoken
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.detail
import app.kestrel.ui.model.label
import app.kestrel.ui.screens.ready.InlinePrimaryButton
import app.kestrel.ui.screens.signinbrowser.BrowserBar
import app.kestrel.ui.screens.signinbrowser.BrowserSheetContent
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing
import java.time.Duration
import java.time.Instant

/**
 * A snipe's listing handed to the user: the host bar with Done, what eBay wants in the snipe's own status words,
 * and the page ([page], the visible WebView; screenshots pass a blank stand-in). Kestrel never solves eBay's checks;
 * Done tells it the user has.
 */
@Composable
fun HandOffScreen(
    state: HandOffUiState,
    onClose: () -> Unit,
    onDone: () -> Unit,
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
                BrowserBar(
                    host = state.page.host,
                    progress = state.page.progress.takeIf { state.url != null },
                    onClose = onClose,
                    // Done ends on the 16 dp column, like every other screen edge (the bar pads 4 dp itself). With the
                    // snipe gone there is nothing to be done with, so the close button is the one way back.
                    actions = {
                        if (!state.gone) {
                            InlinePrimaryButton(
                                "Done",
                                onClick = onDone,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    },
                )
                when {
                    state.gone -> {
                        Gone()
                    }

                    state.snipe != null -> {
                        Guidance(state)
                        page(Modifier.fillMaxWidth().weight(1f))
                    }
                }
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
    state.page.sheet?.let { sheet -> BrowserSheetContent(sheet, onAction = onSheetAction, onDismiss = onSheetDismiss) }
}

/** Which lot, what eBay wants (the status label and detail), and what to do when it is done. */
@Composable
private fun Guidance(state: HandOffUiState) {
    val snipe = state.snipe ?: return
    val status = state.status ?: return
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = Spacing.ColumnA, vertical = 12.dp)) {
            Text(
                text = snipe.title.forDisplay(),
                style = KestrelTheme.type.lotTitleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            StateLabel(status.tone, status.label(state.now))
            bidAtOf(status)?.takeIf { it > state.now }?.let { bidAt -> TimeToBid(bidAt, state.now) }
            Spacer(Modifier.height(4.dp))
            Text(status.detail(state.now).noWidow(), style = MaterialTheme.typography.bodyMedium, color = inkSoft)
            Spacer(Modifier.height(8.dp))
            Text(HINT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        HorizontalDivider(thickness = 1.dp, color = KestrelTheme.catalogue.rule)
    }
}

@Composable
private fun Gone() {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.ColumnA, vertical = 24.dp)) {
        Text(
            text = "This snipe is no longer on your list",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "There is nothing to check on eBay’s page for it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How long is left before Kestrel bids, ticking, as on S3: "9 m 29 s left". The deadline sentence under it names the
 * clock time.
 */
@Composable
private fun TimeToBid(
    bidAt: Instant,
    now: Instant,
) {
    val left = Duration.between(now, bidAt)
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .padding(top = 4.dp)
            .clearAndSetSemantics { contentDescription = "${left.spoken()} left before Kestrel bids." },
    ) {
        RollingDigits(
            text = left.countdown(),
            style = KestrelTheme.type.countdownMedium,
            unitStyle = MaterialTheme.typography.headlineMedium,
            color = ink,
            modifier = Modifier.alignBy(LastBaseline),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "left",
            style = MaterialTheme.typography.headlineMedium,
            color = ink,
            modifier = Modifier.alignBy(LastBaseline),
        )
    }
}

/** When Kestrel bids, for the statuses that hand a page over before the bid. */
private fun bidAtOf(status: LotStatus): Instant? =
    when (status) {
        is LotStatus.Captcha -> status.bidAt
        is LotStatus.PhoneCheck -> status.bidAt
        is LotStatus.NeedsYou -> status.bidAt
        is LotStatus.SignInNeeded -> status.bidAt
        is LotStatus.DryRunFailed -> status.bidAt
        else -> null
    }

/** What to do on eBay's page; Done is in the bar above. */
private const val HINT = "When eBay’s page shows the listing again, tap Done."
