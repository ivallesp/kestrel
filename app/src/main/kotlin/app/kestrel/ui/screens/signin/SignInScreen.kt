package app.kestrel.ui.screens.signin

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kestrel.R
import app.kestrel.model.EbaySite
import app.kestrel.ui.components.PrimaryButton
import app.kestrel.ui.components.SymbolIcon
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.screens.ready.EdgeFade
import app.kestrel.ui.screens.ready.GlyphCircle
import app.kestrel.ui.theme.KestrelTheme
import app.kestrel.ui.theme.Spacing

/** How S6 is left: Close when it was opened over another screen, Back inside onboarding. */
enum class SignInExit(
    @param:DrawableRes val icon: Int,
    val label: String,
) {
    Close(Symbols.Close, "Close"),
    Back(Symbols.ArrowBack, "Back"),
}

@Immutable
private data class Promise(
    @param:DrawableRes val icon: Int,
    val title: String,
    val detail: String,
)

/** eBay UK's sign-in host, named on S6 and shown in the browser's host bar ("signin.ebay.co.uk"). */
val SignInHost: String = "signin." + EbaySite.UK.host.removePrefix("www.")

private val Promises =
    listOf(
        Promise(
            Symbols.Storefront,
            "You sign in on eBay’s own page",
            "The next screen is $SignInHost, shown inside Kestrel. Your password goes to eBay, not to Kestrel.",
        ),
        Promise(
            Symbols.Smartphone,
            "Your session stays on this phone",
            "It is kept here and never sent anywhere else. Sign out any time in Settings.",
        ),
        Promise(
            Symbols.AlternateEmail,
            "Use your email or username and password",
            "Google and Facebook buttons don’t work inside apps. No eBay password yet? Set one on " +
                "${EbaySite.UK.host.removePrefix("www.")} first.",
        ),
        Promise(
            Symbols.Sms,
            "2-step codes are fine",
            "Enter the code from your texts or authenticator app as usual.",
        ),
    )

/**
 * S6, the sign-in introduction (DESIGN S6): the arch frontispiece, four promises and, pinned at the bottom,
 * "Continue to eBay" (no open-in-new glyph: the page opens inside Kestrel), "Not now" and the not-affiliated line.
 */
@Composable
fun SignInScreen(
    onExit: () -> Unit,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    exit: SignInExit = SignInExit.Close,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val scroll = rememberScrollState()
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    // The exit button shares its row with the top of the arch, so the page fits a 411 x 923 dp
                    // phone at normal text size and only scrolls with larger text.
                    Box(Modifier.fillMaxWidth()) {
                        Frontispiece(lock = true, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                        IconButton(onClick = onExit, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                            SymbolIcon(exit.icon, contentDescription = exit.label)
                        }
                    }
                    Column(Modifier.padding(horizontal = Spacing.ColumnA)) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "Sign in to eBay",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text =
                                "Kestrel bids with your own eBay account, from this phone. eBay may ask you to sign " +
                                    "in again now and then.",
                            style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) { Promises.forEach { PromiseRow(it) } }
                        Spacer(Modifier.height(16.dp))
                    }
                }
                // With large text the promises run on under the buttons: fade them out rather than cut a line.
                if (scroll.canScrollForward) EdgeFade(Modifier.align(Alignment.BottomCenter))
            }
            Column(Modifier.padding(start = Spacing.ColumnA, end = Spacing.ColumnA, top = 4.dp, bottom = 8.dp)) {
                PrimaryButton("Continue to eBay", onClick = onContinue)
                TextButton(
                    onClick = onNotNow,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.TouchTarget),
                ) {
                    Text("Not now", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = "Kestrel isn’t made by or affiliated with eBay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PromiseRow(promise: Promise) {
    Row {
        GlyphCircle(
            icon = promise.icon,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            tint = MaterialTheme.colorScheme.onSurface,
            size = 44.dp,
            glyphSize = 24.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                promise.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                promise.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The S6 frontispiece (DESIGN S6, 2.7): MaterialShapes.Arch, 144 x 168 dp, on the launcher's background gradient,
 * with the launcher's kestrel portrait filling its width and, when [lock] is set, a 56 dp Cookie9Sided ink badge
 * with a lock overlapping the arch's bottom-end corner by 12 dp. Decoration only: TalkBack skips it. In dark theme
 * the art is drawn at 88 % like scene photos, so it never outshines the text (DESIGN MF14).
 */
@Composable
fun Frontispiece(
    lock: Boolean,
    modifier: Modifier = Modifier,
    archWidth: Dp = 144.dp,
    archHeight: Dp = 168.dp,
) {
    // Modulate multiplies the colour channels and keeps alpha; Multiply would paint the portrait's transparent
    // corners with the filter colour.
    val dim = ColorFilter.tint(KestrelTheme.catalogue.scenePhotoDim, BlendMode.Modulate)
    Box(modifier.size(width = archWidth, height = archHeight)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(MaterialShapes.Arch.toShape())
                .paint(
                    painterResource(R.drawable.ic_launcher_background),
                    contentScale = ContentScale.FillBounds,
                    colorFilter = dim,
                ),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                colorFilter = dim,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .wrapContentSize(Alignment.BottomCenter, unbounded = true)
                        .requiredSize(archWidth * PORTRAIT_SCALE),
            )
        }
        if (lock) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 12.dp, y = 12.dp)
                    .size(56.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                SymbolIcon(Symbols.Lock, tint = MaterialTheme.colorScheme.onPrimary, size = 24.dp)
            }
        }
    }
}

/**
 * The portrait is a 108 dp adaptive-icon layer whose shoulders span about 69 of its 108 units; drawing it at 1.45
 * times the arch width lets the shoulders fill the arch and the head sit clear of its curve.
 */
private const val PORTRAIT_SCALE = 1.45f
