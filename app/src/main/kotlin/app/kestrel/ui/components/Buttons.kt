package app.kestrel.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing
import app.kestrel.ui.theme.StatusTone

/**
 * A screen's primary action (DESIGN 3.17): filled ink, 56 dp tall, full width, with the Expressive press morph.
 * "Arm snipe", "Continue to eBay", "Paste eBay link", "Save max".
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val height = Spacing.PrimaryButtonHeight
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = icon != null),
        modifier = modifier.fillMaxWidth().heightIn(min = height),
    ) {
        icon?.let {
            SymbolIcon(it, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * An outlined action (DESIGN 3.17): a 1 dp border in the outline colour and ink words, 40 dp tall by default (48 dp
 * for screen-level actions). "Set a max", "Edit max", "Add a snipe to this group".
 */
@Composable
fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    height: Dp = Spacing.InlineButtonHeight,
    enabled: Boolean = true,
) {
    val outline = MaterialTheme.colorScheme.outline
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        border = BorderStroke(1.dp, if (enabled) outline else outline.copy(alpha = DISABLED_ALPHA)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = icon != null),
        modifier = modifier.heightIn(min = height),
    ) {
        icon?.let {
            SymbolIcon(it, size = ButtonDefaults.iconSizeFor(height))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

private const val DISABLED_ALPHA = 0.38f

/**
 * A button in a state family's colours (DESIGN 3.17 "State tonal"): the family container with on-container words,
 * 40 dp tall inside a 48 dp touch target. "Raise max", "Open eBay page", "Sign in" on attention rows.
 */
@Composable
fun StateTonalButton(
    tone: StatusTone,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val colors = KestrelTheme.status[tone]
    val height = Spacing.InlineButtonHeight
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.container,
                contentColor = colors.onContainer,
            ),
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = icon != null),
        modifier = modifier.heightIn(min = height),
    ) {
        icon?.let {
            SymbolIcon(it, size = ButtonDefaults.iconSizeFor(height))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Connected toggles where exactly one option is chosen (DESIGN 3.17): lead time 3 s / 6 s / 10 s and the theme.
 * Unchecked options sit on secondaryContainer; the checked one is ink with an 18 dp check. 48 dp tall, read as
 * radio buttons.
 */
@Composable
fun ConnectedToggleRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = index == selectedIndex
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(index) },
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = Spacing.TouchTarget)
                        .semantics { role = Role.RadioButton },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                        checkedContainerColor = scheme.primary,
                        checkedContentColor = scheme.onPrimary,
                    ),
            ) {
                if (checked) {
                    SymbolIcon(Symbols.Check, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(option, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
