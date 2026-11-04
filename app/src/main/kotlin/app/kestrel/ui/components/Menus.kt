package app.kestrel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A menu (DESIGN 2.9): the default DropdownMenu, surfaceContainer with its shadow, plus a 1 dp edge in the outline
 * colour. surfaceContainer is close to the page and to the cards, so the edge keeps the menu apart from what is under
 * it even where no shadow is drawn (Q12: 3:1 for edges).
 */
@Composable
fun KestrelMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        border = menuBorder(),
        content = content,
    )
}

/** The menu's 1 dp edge, for [KestrelMenu] and anything that draws a menu's items in place. */
@Composable
fun menuBorder(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
