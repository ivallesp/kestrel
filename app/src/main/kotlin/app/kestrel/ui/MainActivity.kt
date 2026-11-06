package app.kestrel.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.kestrel.KestrelApp
import app.kestrel.data.ThemeMode
import app.kestrel.ui.celebration.AndroidCelebrationSound
import app.kestrel.ui.celebration.CelebrationSound
import app.kestrel.ui.celebration.LocalCelebrationSound
import app.kestrel.ui.shell.Destination
import app.kestrel.ui.shell.KestrelShell
import app.kestrel.ui.shell.LaunchIntents
import app.kestrel.ui.theme.KestrelTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The only activity (singleTask). It draws edge to edge under the splash screen and hosts the app shell.
 *
 * - The launcher opens Home, or Onboarding on the first launch.
 * - A shared text (ACTION_SEND, text/plain) opens Add with the text.
 * - A notification's snipe link (AppLinks) opens that snipe, in onCreate and in onNewIntent.
 * - It never shows over the lock screen: no alert uses a full-screen intent (DESIGN 5.4), and the hand-off page is
 *   signed in to the user's eBay account, so a notification tap asks for the unlock first.
 */
class MainActivity : ComponentActivity() {
    private val incoming = Channel<Destination>(Channel.BUFFERED)

    @Volatile
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { !contentReady }
        val app = application as KestrelApp
        // A restored activity already has its back stack; only a fresh start reads the launch intent.
        val asked = if (savedInstanceState == null) LaunchIntents.destinationOf(intent) else null
        setContent {
            val onboardingDone by app.uiPreferences.onboardingDone.collectAsStateWithLifecycle(initialValue = null)
            val settings by app.container.settings.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val done = onboardingDone ?: return@setContent
            val themeMode = settings?.themeMode ?: return@setContent
            SideEffect { contentReady = true }
            val dark =
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            DisposableEffect(dark) {
                // Dark icons on paper, light icons in dark theme (DESIGN Q23); S3 sets light icons over its photo.
                enableEdgeToEdge(
                    statusBarStyle = barStyle(dark, Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = barStyle(dark, LIGHT_NAVIGATION_SCRIM, DARK_NAVIGATION_SCRIM),
                )
                onDispose {}
            }
            val startStack = remember { LaunchIntents.startStack(done, asked) }
            val requests = remember { incoming.receiveAsFlow() }
            // The win celebration plays its sound unless it is turned off in Settings.
            val soundOn = settings?.celebrationSound != false
            val context = LocalContext.current
            val celebrationSound: () -> CelebrationSound? =
                remember(soundOn, context) { { if (soundOn) AndroidCelebrationSound(context) else null } }
            KestrelTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalCelebrationSound provides celebrationSound) {
                    KestrelShell(
                        startStack = startStack,
                        onFinishOnboarding = { lifecycleScope.launch { app.uiPreferences.markOnboardingDone() } },
                        onExit = ::finish,
                        incoming = requests,
                        afterOnboarding = asked.takeIf { !done },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LaunchIntents.destinationOf(intent)?.let { incoming.trySend(it) }
    }

    private companion object {
        // The scrims enableEdgeToEdge uses for three-button navigation; gesture navigation shows none.
        val LIGHT_NAVIGATION_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_NAVIGATION_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

        fun barStyle(
            dark: Boolean,
            lightScrim: Int,
            darkScrim: Int,
        ): SystemBarStyle = if (dark) SystemBarStyle.dark(darkScrim) else SystemBarStyle.light(lightScrim, darkScrim)
    }
}
