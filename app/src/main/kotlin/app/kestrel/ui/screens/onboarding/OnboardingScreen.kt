package app.kestrel.ui.screens.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.kestrel.data.Settings
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.noWidow
import app.kestrel.ui.format.secondsText
import app.kestrel.ui.screens.ready.EdgeFade
import app.kestrel.ui.screens.ready.TopBarHeight
import app.kestrel.ui.screens.signin.Frontispiece
import app.kestrel.ui.screens.signin.SignInExit
import app.kestrel.ui.screens.signin.SignInScreen
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing

/** What the onboarding screen can do; it only calls these. */
interface OnboardingActions {
    fun next()

    fun skip()

    fun continueToEbay()

    fun notNow()

    fun back()

    fun allowNotifications()
}

/**
 * The first launch: what Kestrel does in three short pages, then S6 and the notifications permission, asked for in
 * context. Steps slide once; with reduced motion they cut.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val reduceMotion = KestrelTheme.reduceMotion
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state.step,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            if (reduceMotion) {
                ContentTransform(EnterTransition.None, ExitTransition.None)
            } else {
                val forward = targetState.ordinal > initialState.ordinal
                val enter =
                    slideInHorizontally(
                        motion.defaultSpatialSpec(),
                    ) { width -> if (forward) width / 8 else -width / 8 } +
                        fadeIn(motion.defaultEffectsSpec())
                val exit =
                    slideOutHorizontally(motion.defaultSpatialSpec()) { width ->
                        if (forward) -width / 12 else width / 12
                    } +
                        fadeOut(motion.fastEffectsSpec())
                enter togetherWith exit
            }
        },
        label = "onboarding step",
    ) { step ->
        when (step) {
            OnboardingStep.LastSeconds, OnboardingStep.YourMax, OnboardingStep.ThisPhone -> {
                IntroPage(step, actions, windowInsets)
            }

            OnboardingStep.SignIn -> {
                SignInScreen(
                    onExit = actions::back,
                    onContinue = actions::continueToEbay,
                    onNotNow = actions::notNow,
                    exit = SignInExit.Back,
                    windowInsets = windowInsets,
                )
            }

            OnboardingStep.Notifications -> {
                NotificationsPage(actions, windowInsets)
            }
        }
    }
}

@Immutable
private data class Page(
    val overline: String,
    val title: String,
    val body: String,
    @param:DrawableRes val glyph: Int?,
)

private fun pageOf(step: OnboardingStep): Page =
    when (step) {
        OnboardingStep.LastSeconds -> {
            Page(
                overline = "Welcome to Kestrel",
                title = "Bids in the last seconds",
                body = "Kestrel bids for you in the last seconds of an eBay auction, up to the maximum you set.",
                glyph = null,
            )
        }

        OnboardingStep.YourMax -> {
            Page(
                overline = "Your maximum",
                title = "Never above your max",
                body =
                    "You choose the most you would pay and see it with postage. eBay charges one step above the next " +
                        "bidder, never more than your max.",
                glyph = Symbols.Payments,
            )
        }

        else -> {
            Page(
                overline = "This phone",
                title = "Bids from this phone",
                body =
                    "Kestrel bids with your own eBay account, from this phone, $DEFAULT_LEAD before the end. " +
                        "Keep the phone switched on and online when an auction ends.",
                glyph = Symbols.Smartphone,
            )
        }
    }

/** One of the three pages: the art, an overline, the title and one paragraph; Skip at the top, Next at the bottom. */
@Composable
private fun IntroPage(
    step: OnboardingStep,
    actions: OnboardingActions,
    windowInsets: WindowInsets,
) {
    val page = pageOf(step)
    val index = OnboardingStep.intro.indexOf(step)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
            Row(
                Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = actions::skip) { Text("Skip", style = MaterialTheme.typography.labelLarge) }
            }
            CentredContent(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(ArtHeight), contentAlignment = Alignment.Center) {
                    if (page.glyph == null) Frontispiece(lock = false) else Badge(page.glyph)
                }
                Spacer(Modifier.height(40.dp))
                PageWords(page)
            }
            Column(
                Modifier.padding(start = Spacing.ColumnA, end = Spacing.ColumnA, top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageDots(index = index, count = OnboardingStep.intro.size)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(if (step == OnboardingStep.ThisPhone) "Continue" else "Next", onClick = actions::next)
            }
        }
    }
}

/** The permission, asked for where it makes sense: what Kestrel tells you, then Android's own question. */
@Composable
private fun NotificationsPage(
    actions: OnboardingActions,
    windowInsets: WindowInsets,
) {
    val page =
        Page(
            overline = "Alerts",
            title = "Hear when it matters",
            body =
                "Kestrel tells you when you win, when the price passes your max, and when eBay needs you before a " +
                    "bid. Allow notifications so you don’t miss one.",
            glyph = Symbols.Notifications,
        )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
            Spacer(Modifier.height(TopBarHeight))
            CentredContent(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(ArtHeight), contentAlignment = Alignment.Center) {
                    Badge(Symbols.Notifications)
                }
                Spacer(Modifier.height(40.dp))
                PageWords(page)
            }
            Column(Modifier.padding(start = Spacing.ColumnA, end = Spacing.ColumnA, top = 8.dp, bottom = 16.dp)) {
                PrimaryButton("Allow notifications", onClick = actions::allowNotifications)
                TextButton(
                    onClick = actions::notNow,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.TouchTarget),
                ) {
                    Text("Not now", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun PageWords(page: Page) {
    Column {
        SmallCaps(page.overline)
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        // Body copy keeps the paragraph measure. Three lines at least on every page, so the words never jump
        // between pages whose paragraphs are two or three lines long.
        Text(
            text = page.body.noWidow(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = BODY_LINES,
        )
    }
}

/** Neutral decoration (DESIGN U1, Q16): a Cookie9Sided badge in secondaryContainer with an ink glyph. */
@Composable
private fun Badge(
    @DrawableRes glyph: Int,
) {
    Box(
        Modifier
            .size(BadgeSize)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        SymbolIcon(glyph, tint = MaterialTheme.colorScheme.onSecondaryContainer, size = 56.dp)
    }
}

/** Which of the three pages this is, as dots; TalkBack reads "Page 1 of 3". */
@Composable
private fun PageDots(
    index: Int,
    count: Int,
) {
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Page ${index + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { dot ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (dot ==
                            index
                        ) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            )
        }
    }
}

/**
 * The art and words of a page, centred in the space above the buttons; they scroll when large text makes them
 * taller than it, and fade out at its edge rather than being cut through a line.
 */
@Composable
private fun CentredContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scroll = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .heightIn(min = maxHeight)
                    .padding(horizontal = Spacing.ColumnA, vertical = 24.dp),
            // A little above the middle: the optical centre of the space above the buttons.
            verticalArrangement = Arrangement.aligned(BiasAlignment.Vertical(OPTICAL_CENTRE)),
            content = content,
        )
        if (scroll.canScrollForward) EdgeFade(Modifier.align(Alignment.BottomCenter))
    }
}

private val ArtHeight = 196.dp

/** Every page's paragraph takes at least this many lines (the longest at font scale 1). */
private const val BODY_LINES = 3
private const val OPTICAL_CENTRE = -0.3f
private val BadgeSize = 144.dp

/** The default lead time the third page names ("6 s"). */
private val DEFAULT_LEAD = secondsText(Settings.DEFAULT_LEAD_SECONDS.toLong())
