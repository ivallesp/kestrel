package app.kestrel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether [KestrelTheme] is dark. Read it through [KestrelTheme.isDark]. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Kestrel's theme: Material 3 Expressive with the fixed paper-and-ink palette (no dynamic colour), the expressive
 * motion scheme, the catalogue shapes and type, the status palette and the reduced-motion flag.
 *
 * Screenshot tests pass [darkTheme] and [reduceMotion] explicitly so a render never depends on the machine.
 */
@Composable
fun KestrelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = rememberReduceMotion(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStatusPalette provides if (darkTheme) DarkStatusPalette else LightStatusPalette,
        LocalCatalogueColors provides if (darkTheme) DarkCatalogueColors else LightCatalogueColors,
        LocalCatalogueType provides KestrelCatalogueType,
        LocalReduceMotion provides reduceMotion,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) KestrelDarkColors else KestrelLightColors,
            motionScheme = MotionScheme.expressive(),
            shapes = KestrelShapes,
            typography = KestrelTypography,
            content = content,
        )
    }
}

/** Kestrel's additions to MaterialTheme: status tones, catalogue colours and type, theme and motion flags. */
object KestrelTheme {
    val status: StatusPalette
        @Composable @ReadOnlyComposable
        get() = LocalStatusPalette.current

    val catalogue: CatalogueColors
        @Composable @ReadOnlyComposable
        get() = LocalCatalogueColors.current

    val type: CatalogueType
        @Composable @ReadOnlyComposable
        get() = LocalCatalogueType.current

    /** True in dark theme, whichever way it was chosen (system, or the theme setting). */
    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalDarkTheme.current

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalReduceMotion.current
}
