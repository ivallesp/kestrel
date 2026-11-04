package app.kestrel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.SheetShape

/**
 * The offline banner of S1 and S3 (DESIGN 3.20): surfaceContainerHigh, 20 dp corners, cloud_off and two lines.
 * "Offline since 18:02" / "Prices are from 18:02. Kestrel can't bid until the phone reconnects."
 */
@Composable
fun OfflineBanner(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SymbolIcon(Symbols.CloudOff, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The LoadingIndicator of DESIGN 3.20, which appears in exactly three places: S2 while fetching ([contained], 48 dp),
 * waiting for eBay after a bid and reading the result (24 dp in the Bidding accent), and pull-to-refresh. With reduced
 * motion it is a static hourglass in the same colour; the words next to it stay the same.
 */
@Composable
fun CalmLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = KestrelTheme.status.bidding.accent,
    size: Dp = 24.dp,
    contained: Boolean = false,
) {
    when {
        KestrelTheme.reduceMotion -> {
            val container =
                if (contained) {
                    Modifier
                        .clip(
                            CircleShape,
                        ).background(LoadingIndicatorDefaults.containedContainerColor)
                } else {
                    Modifier
                }
            Box(modifier.size(size).then(container), contentAlignment = Alignment.Center) {
                SymbolIcon(Symbols.HourglassTop, tint = color, size = size * if (contained) 0.5f else 0.8f)
            }
        }

        contained -> {
            ContainedLoadingIndicator(modifier = modifier.size(size), indicatorColor = color)
        }

        else -> {
            LoadingIndicator(modifier = modifier.size(size), color = color)
        }
    }
}

/**
 * A bottom sheet in Kestrel's style (DESIGN 3.16): surfaceContainerLow, 28 dp top corners, a 32 x 4 dp handle in the
 * outline colour, a 32 % black scrim, and [SheetLayout] padding with the title in headlineSmall. Sheets with a text
 * field pass a state with skipPartiallyExpanded = true and keep their primary button last, so it stays above the
 * keyboard.
 */
@Composable
fun KestrelBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = { SheetDragHandle() },
        contentWindowInsets = { WindowInsets(0) },
    ) {
        SheetLayout(
            title = title,
            windowInsets = WindowInsets.ime.union(WindowInsets.navigationBars),
            content = content,
        )
    }
}

/** The handle at the top of every sheet: 32 x 4 dp in the outline colour. */
@Composable
fun SheetDragHandle(modifier: Modifier = Modifier) {
    BottomSheetDefaults.DragHandle(
        modifier = modifier,
        width = 32.dp,
        height = 4.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}

/**
 * The inside of a sheet: 16 dp at the sides, 8 dp at the top, 16 dp at the bottom plus [windowInsets] (the keyboard
 * and the navigation bar), and the title in headlineSmall as a heading. Screenshot tests draw it without a window.
 */
@Composable
fun SheetLayout(
    title: String,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        content()
    }
}
