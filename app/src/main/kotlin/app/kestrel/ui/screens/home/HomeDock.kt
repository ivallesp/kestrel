package app.kestrel.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.ReadinessPillShape
import app.kestrel.ui.theme.Spacing

/**
 * The dock (DESIGN 3.8): the readiness pill and the Add button on surfaceContainerLow with a 1 dp top rule, 16 dp at
 * the sides and 8 dp above and below, then the navigation-bar inset. It never floats and nothing scrolls under it.
 * [showAdd] is false in the empty state, where the page has its own "Paste eBay link".
 */
@Composable
internal fun HomeDock(
    pill: ReadinessPill,
    showAdd: Boolean,
    windowInsets: WindowInsets,
    onOpenReady: () -> Unit,
    onPillAction: (PillAction) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rule = KestrelTheme.catalogue.rule
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind { drawLine(rule, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier =
                Modifier
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = Spacing.ColumnA, vertical = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadinessPillView(pill, onClick = onOpenReady, onAction = onPillAction, modifier = Modifier.weight(1f))
            if (showAdd) {
                Spacer(Modifier.width(Spacing.Small))
                AddButton(onAdd)
            }
        }
    }
}

/**
 * The readiness pill: one tap opens S5. A problem sits on the Attention container with its own glyph and, when there
 * is one, a filled ink button (Fix, Review or Sign in). Line 1 never truncates; line 2 ellipsises.
 */
@Composable
private fun ReadinessPillView(
    pill: ReadinessPill,
    onClick: () -> Unit,
    onAction: (PillAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attention = KestrelTheme.status.attention
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        targetValue = if (pill.needsAttention) attention.container else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = effects,
        label = "pill container",
    )
    val reduceMotion = KestrelTheme.reduceMotion
    val motion = MaterialTheme.motionScheme
    val fadeIn = motion.defaultEffectsSpec<Float>()
    val fadeOut = motion.fastEffectsSpec<Float>()
    val sizeSpec = motion.defaultSpatialSpec<IntSize>()
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "${pill.title}. ${pill.detail}" },
        shape = ReadinessPillShape,
        color = container,
        border = if (pill.needsAttention) null else BorderStroke(1.dp, KestrelTheme.catalogue.rule),
    ) {
        AnimatedContent(
            targetState = pill,
            transitionSpec = {
                if (reduceMotion) {
                    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                } else {
                    fadeIn(fadeIn) togetherWith fadeOut(fadeOut) using SizeTransform(clip = false) { _, _ -> sizeSpec }
                }
            },
            contentAlignment = Alignment.CenterStart,
            label = "pill",
        ) { shown ->
            PillContent(shown, onAction)
        }
    }
}

@Composable
private fun PillContent(
    pill: ReadinessPill,
    onAction: (PillAction) -> Unit,
) {
    val attention = KestrelTheme.status.attention
    val scheme = MaterialTheme.colorScheme
    val iconTint =
        when (pill.kind) {
            PillKind.Ready -> KestrelTheme.status.won.accent
            PillKind.Checking -> scheme.onSurfaceVariant
            else -> attention.accent
        }
    val titleColour = if (pill.needsAttention) attention.onContainer else scheme.onSurface
    val detailColour = if (pill.needsAttention) attention.onContainer else scheme.onSurfaceVariant
    val action = pill.action
    Row(
        modifier =
            Modifier
                .defaultMinSize(minHeight = PillHeight)
                .padding(
                    start = 12.dp,
                    end = if (action != null && action != PillAction.Review) 8.dp else 12.dp,
                    top = 4.dp,
                    bottom = 4.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(pill.icon, tint = iconTint, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clearAndSetSemantics {}) {
            Text(pill.title, style = KestrelTheme.type.stateWord, color = titleColour, maxLines = 1)
            Text(
                text = pill.detail,
                style = MaterialTheme.typography.bodySmall,
                color = detailColour,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (action) {
            null -> {
                Unit
            }

            // "Review" would only repeat what a tap on the pill does (open S5), so a chevron says it instead and
            // line 1 keeps its width.
            PillAction.Review -> {
                Spacer(Modifier.width(Spacing.Small))
                SymbolIcon(Symbols.ChevronRight, tint = titleColour, size = 20.dp)
            }

            else -> {
                Spacer(Modifier.width(Spacing.Small))
                Button(
                    onClick = { onAction(action) },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.heightIn(min = Spacing.InlineButtonHeight),
                ) {
                    Text(action.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private val PillHeight = 56.dp

/** Above this font scale the dock gives the pill's words the room and Add keeps only its glyph. */
private const val COMPACT_ADD_FONT_SCALE = 1.2f

/**
 * "Add": filled ink, 56 dp, with the add glyph. TalkBack reads "Add snipe". With large text it is the glyph alone in
 * a 56 dp circle, so the pill's first line and its consequence still fit beside it.
 */
@Composable
private fun AddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = Spacing.PrimaryButtonHeight
    val glyphOnly = LocalDensity.current.fontScale >= COMPACT_ADD_FONT_SCALE
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = if (glyphOnly) PaddingValues(0.dp) else PaddingValues(start = 16.dp, end = 20.dp),
        modifier =
            modifier
                .heightIn(min = height)
                .then(if (glyphOnly) Modifier.size(height) else Modifier)
                .semantics { contentDescription = "Add snipe" },
    ) {
        if (glyphOnly) {
            SymbolIcon(Symbols.Add, size = 24.dp)
        } else {
            SymbolIcon(Symbols.Add, size = 20.dp)
            Spacer(Modifier.width(Spacing.Small))
            Text("Add", style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
