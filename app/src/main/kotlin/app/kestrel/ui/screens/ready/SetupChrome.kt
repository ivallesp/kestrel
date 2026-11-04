package app.kestrel.ui.screens.ready

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.theme.Spacing

// Small pieces the setup screens share (S5, S6, the eBay pages, Settings and Onboarding).

/**
 * A screen's top bar: the back or close button at the start (48 dp, 4 dp from the edge) and optional actions at the
 * end. Flat on the page colour; the screen pads it below the status bar.
 */
@Composable
internal fun SetupTopBar(
    @DrawableRes navigationIcon: Int,
    navigationLabel: String,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigate) { SymbolIcon(navigationIcon, contentDescription = navigationLabel) }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** The top bar is 56 dp tall, so a 48 dp button sits with 4 dp around it. */
internal val TopBarHeight = 56.dp

/**
 * An inline filled button in ink (DESIGN 3.17, "Primary ... 40 dp inline"): "Fix" on S5 rows, "Done" on the eBay
 * page. 40 dp tall inside a 48 dp touch target.
 */
@Composable
internal fun InlinePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(Spacing.InlineButtonHeight),
        modifier = modifier.heightIn(min = Spacing.InlineButtonHeight),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** An inline tonal button (DESIGN 3.17, "Tonal"): "Turn on", "Sign out". 40 dp inside a 48 dp touch target. */
@Composable
internal fun InlineTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        contentPadding = ButtonDefaults.contentPaddingFor(Spacing.InlineButtonHeight),
        modifier = modifier.heightIn(min = Spacing.InlineButtonHeight),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * A 24 dp fade to the page colour at the bottom edge of a scrolling area that ends above pinned buttons, so text
 * that runs on below fades out instead of being cut through a line. Place it only while more is below.
 */
@Composable
internal fun EdgeFade(modifier: Modifier = Modifier) {
    val page = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Brush.verticalGradient(listOf(page.copy(alpha = 0f), page))),
    )
}

/** A glyph centred in a filled circle: the leading icon of S5 and Settings rows and of the S6 promises. */
@Composable
internal fun GlyphCircle(
    @DrawableRes icon: Int,
    container: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    glyphSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        SymbolIcon(icon, tint = tint, size = glyphSize)
    }
}
