package app.kestrel.ui.celebration

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.screens.add.InkTextButton
import app.kestrel.ui.screens.add.TonalButton
import app.kestrel.ui.screens.result.ResultUiState
import app.kestrel.ui.screens.result.ResultVariant
import app.kestrel.ui.shell.KestrelNavigator
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing
import kotlinx.coroutines.flow.first

/** What the celebration says under the scene: a won lot's own facts, or the preview's explanation. */
@Immutable
data class CelebrationContent(
    val title: String,
    val summary: String?,
    val timing: String?,
    val preview: Boolean,
) {
    companion object {
        /** From Settings: no snipe, no money, nothing bid; it only shows what a win looks like. */
        val Preview =
            CelebrationContent(
                title = "Your next win",
                summary = null,
                timing = null,
                preview = true,
            )

        /** A verified win's result: "Won for £101.00 · £19.00 under your £120.00 max" and eBay's timing line. */
        fun of(state: ResultUiState): CelebrationContent {
            val paid = state.paid
            val summary =
                paid?.let {
                    val under = listOfNotNull(it.under, it.maxLine).joinToString(" ").ifEmpty { null }
                    listOfNotNull("Won for ${it.wonFor}", under).joinToString(" · ")
                }
            return CelebrationContent(state.title, summary, state.quote?.timing, preview = false)
        }
    }
}

/**
 * Where "Buy me a coffee" leads. It is not set up yet, so [url] is null and the button explains that instead of
 * opening anything: Kestrel invents no payment page, account or donation set-up.
 */
object CoffeeLink {
    val url: String? = null

    const val PLACEHOLDER = "The coffee link isn’t set up yet."

    /** Opens the link in the browser; false while there is none (or no browser), so the caller says why. */
    fun open(context: Context): Boolean {
        val address = url ?: return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, address.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}

/**
 * The win celebration, full screen over the result: the kestrel's scene above, the won lot and the coffee note on
 * the page below. It plays once (about six seconds), a tap on the scene skips to the end, and with reduced motion it
 * opens on the last frame. [frameMillis] fixes the moment, for screenshots and tests. The preview ([content] from
 * [CelebrationContent.Preview]) can play again; it never touches a snipe.
 */
@Composable
fun WinCelebration(
    content: CelebrationContent,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    frameMillis: Long? = null,
) {
    val reduceMotion = KestrelTheme.reduceMotion
    var run by remember { mutableIntStateOf(0) }
    val clock = remember(run) { mutableLongStateOf(frameMillis ?: if (reduceMotion) CelebrationTimeline.END else 0L) }
    val playing = frameMillis == null && !reduceMotion
    val haptics = LocalHapticFeedback.current
    if (playing) {
        LaunchedEffect(run) {
            val start = withFrameMillis { it }
            while (clock.longValue < CelebrationTimeline.END) {
                withFrameMillis { frame -> clock.longValue = (frame - start).coerceAtMost(CelebrationTimeline.END) }
            }
        }
        LaunchedEffect(run) {
            // One tick as the talons close on the tag; a skipped celebration has nothing to feel.
            snapshotFlow { clock.longValue }.first { it >= CelebrationTimeline.STRIKE }
            if (clock.longValue < CelebrationTimeline.END) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    // A fixed frame is read as it is; a playing one from the clock, in the draw and layer phases only.
    val time: () -> Long = if (frameMillis != null) ({ frameMillis }) else ({ clock.longValue })
    BackHandler(onBack = onDone)
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val large = LocalDensity.current.fontScale > LARGE_TEXT
            val bandHeight = maxHeight * if (large) 0.38f else 0.47f
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(bandHeight)) {
                        CelebrationBand(
                            time = time,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clickable(enabled = playing, onClickLabel = "Skip to the end") {
                                        clock.longValue = CelebrationTimeline.END
                                    }.semantics { contentDescription = SCENE_DESCRIPTION },
                        )
                        IconButton(
                            onClick = onDone,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = sceneInk()),
                            modifier =
                                Modifier
                                    .windowInsetsPadding(
                                        windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Start),
                                    ).padding(4.dp),
                        ) {
                            SymbolIcon(Symbols.Close, contentDescription = "Close")
                        }
                    }
                    CelebrationSheet(content = content, time = time, windowInsets = windowInsets)
                }
                CelebrationActions(
                    preview = content.preview,
                    onDone = onDone,
                    onPlayAgain =
                        {
                            run += 1
                            Unit
                        }.takeIf { content.preview && !reduceMotion && frameMillis == null },
                    windowInsets = windowInsets,
                )
            }
        }
    }
}

/** The scene, drawn on every frame from [time] without recomposing anything around it. */
@Composable
internal fun CelebrationBand(
    time: () -> Long,
    modifier: Modifier = Modifier,
) {
    val palette = if (KestrelTheme.isDark) ScenePalette.Dusk else ScenePalette.Morning
    val measurer = rememberTextMeasurer()
    val tagStyle = KestrelTheme.type.smallCaps
    Box(
        modifier.drawWithCache {
            val unit = SceneLayout(size).unit
            // The tag's word is part of the drawing, so it keeps its size whatever the text scale.
            val style =
                TextStyle(
                    fontFamily = tagStyle.fontFamily,
                    fontWeight = tagStyle.fontWeight,
                    letterSpacing = tagStyle.letterSpacing,
                )
            val word = measurer.measure(TAG_WORD, style.copy(fontSize = (4.6f * unit).toSp()))
            onDrawBehind { drawCelebrationScene(time(), palette, word) }
        },
    )
}

@Composable
private fun CelebrationSheet(
    content: CelebrationContent,
    time: () -> Long,
    windowInsets: WindowInsets,
) {
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            // The page overlaps the scene's lower edge, like the sheet over a lot's photograph.
            .layout { measurable, constraints ->
                val overlap = SHEET_OVERLAP.roundToPx()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, -overlap) }
            }.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
            .padding(start = Spacing.ColumnA, end = Spacing.ColumnA, top = 24.dp, bottom = Spacing.Large),
    ) {
        // The words come in three beats: the lot as the bird hovers, the price as it strikes, the coffee at the end.
        Column(Modifier.reveal(time, CelebrationTimeline.TITLE_FROM)) {
            if (content.preview) {
                SmallCaps("Preview")
                Spacer(Modifier.height(4.dp))
            }
            Text(
                "You won",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                content.title,
                style = KestrelTheme.type.lotTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.reveal(time, CelebrationTimeline.LABEL_FROM)) {
            content.summary?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            content.timing?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = inkSoft)
            }
            if (content.preview) {
                Spacer(Modifier.height(8.dp))
                Text(PREVIEW_NOTE, style = MaterialTheme.typography.bodyMedium, color = inkSoft)
            }
        }
        Spacer(Modifier.height(20.dp))
        CoffeeNote(Modifier.reveal(time, CelebrationTimeline.COFFEE_FROM))
    }
}

/** The way out, docked under the page so it is always in reach: the receipt, or (preview) done and play again. */
@Composable
private fun CelebrationActions(
    preview: Boolean,
    onDone: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    windowInsets: WindowInsets,
) {
    val bottom = windowInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
            .padding(
                start = Spacing.ColumnA,
                end = Spacing.ColumnA,
                top = Spacing.Small,
                bottom =
                    bottom + Spacing.Large,
            ),
    ) {
        if (onPlayAgain != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                InkTextButton("Play again", onClick = onPlayAgain)
                PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.weight(1f))
            }
        } else {
            PrimaryButton(
                text = if (preview) "Done" else "See the receipt",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The coffee note at the end of the celebration: Kestrel has no ads and earns nothing, and a coffee is the only way to
 * say thanks. The link is a placeholder until one is set ([CoffeeLink]).
 */
@Composable
fun CoffeeNote(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var explained by remember { mutableStateOf(false) }
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KestrelTheme.catalogue.card)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SymbolIcon(Symbols.Coffee, tint = inkSoft, size = 20.dp)
            SmallCaps("No ads · no fees")
        }
        Spacer(Modifier.height(8.dp))
        Text(COFFEE_NOTE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TonalButton(
                text = "Buy me a coffee",
                icon = Symbols.Coffee,
                onClick = { if (!CoffeeLink.open(context)) explained = true },
            )
            Text(
                if (explained) "Not set up yet: there’s no link to open." else "Link coming soon",
                style = MaterialTheme.typography.bodySmall,
                color = inkSoft,
            )
        }
    }
}

/** Fades in and rises 16 dp from [from], on the layer, so the frames never recompose the text. */
private fun Modifier.reveal(
    time: () -> Long,
    from: Long,
): Modifier =
    graphicsLayer {
        val p = easeOut(CelebrationTimeline.progress(time(), from, from + REVEAL_MILLIS))
        alpha = p
        translationY = (1f - p) * 16.dp.toPx()
    }

/** Ink on the morning sky, paper on the dusk sky: the close button sits on the scene. */
@Composable
private fun sceneInk(): Color = if (KestrelTheme.isDark) Color(0xFFEDE6D9) else Color(0xFF1E1B16)

private const val LARGE_TEXT = 1.3f
private const val REVEAL_MILLIS = 600L
private val SHEET_OVERLAP = 28.dp
private const val TAG_WORD = "WON"

private const val SCENE_DESCRIPTION =
    "A kestrel hovers in the wind, dives onto your lot’s tag in the grass and carries it to a fence post."

private const val PREVIEW_NOTE = "This plays the first time you open a win. Nothing was bid."

/** No ads and no money made, in the bird's own terms. */
const val COFFEE_NOTE =
    "Kestrel hunts for you alone: no ads, no fees, nothing sold. If it caught you a good one, you can buy me a coffee."

/**
 * A result page that celebrates a verified win the first time it opens: the celebration covers the page, and the
 * page's WON seal stamps once the celebration closes. Every other result, and every later open, shows the page alone.
 */
@Composable
fun CelebratedResult(
    state: ResultUiState,
    windowInsets: WindowInsets,
    page: @Composable (sealHeld: Boolean) -> Unit,
) {
    val celebrate = state.variant == ResultVariant.Won && state.stampSeal
    var showing by rememberSaveable(state.id) { mutableStateOf(celebrate) }
    page(showing)
    if (showing) {
        WinCelebration(CelebrationContent.of(state), onDone = { showing = false }, windowInsets = windowInsets)
    }
}

/** Settings' preview of the celebration (Destination.Celebration): sample words only, nothing bid or stored. */
@Composable
fun CelebrationPreviewDestination(
    navigator: KestrelNavigator,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    WinCelebration(
        CelebrationContent.Preview,
        onDone = navigator::back,
        modifier = modifier,
        windowInsets = windowInsets,
    )
}
