package app.kestrel.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user turned animations off (Settings > Accessibility > Remove animations, which sets
 * Settings.Global.ANIMATOR_DURATION_SCALE to 0). DESIGN 2.10: screen transitions become None, shared elements cut,
 * the final-minute wave is flat, digits swap without rolling, the WON seal appears without its stamp, and
 * LoadingIndicator becomes a static hourglass with the same words. Every animation still takes its spec from
 * MaterialTheme.motionScheme (MotionScheme.expressive()).
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Whether animations are off now; follows the setting while the screen is up. */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduceMotion by remember(resolver) { mutableStateOf(animationsOff(resolver)) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduceMotion = animationsOff(resolver)
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduceMotion
}

private fun animationsOff(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
