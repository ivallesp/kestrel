package app.kestrel.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Paper and ink (DESIGN 2.1). Kestrel uses this fixed palette instead of wallpaper colour, so any colour on screen is
// a state colour (StatusColors.kt). The phone and the screenshots use the same values.

private val Ink = Color(0xFF1E1B16)
private val InkSoft = Color(0xFF5C5549)
private val Paper = Color(0xFFF5F0E7)
private val PaperCard = Color(0xFFFFFCF7)
private val DarkInk = Color(0xFFEDE6D9)
private val DarkInkSoft = Color(0xFFBCB2A3)
private val DarkPage = Color(0xFF15130F)

val KestrelLightColors: ColorScheme =
    lightColorScheme(
        primary = Ink,
        onPrimary = Color(0xFFFBF7F0),
        primaryContainer = Color(0xFFE7DFD2),
        onPrimaryContainer = Ink,
        inversePrimary = Color(0xFFD9CFBF),
        secondary = InkSoft,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE9E1D3),
        onSecondaryContainer = Ink,
        tertiary = Color(0xFF2D5275),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFDEE7F0),
        onTertiaryContainer = Color(0xFF16324D),
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = Color(0xFFE9E1D3),
        onSurfaceVariant = InkSoft,
        surfaceTint = Color(0xFF8C8375),
        inverseSurface = Color(0xFF2F2B25),
        inverseOnSurface = Color(0xFFF4EFE6),
        error = Color(0xFFA3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDA),
        onErrorContainer = Color(0xFF5C130E),
        outline = Color(0xFF8C8375),
        outlineVariant = Color(0xFFD9D0C1),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFBF8F2),
        surfaceDim = Color(0xFFE2DACB),
        surfaceContainerLowest = PaperCard,
        surfaceContainerLow = Color(0xFFFAF6EF),
        surfaceContainer = Color(0xFFF0EADF),
        surfaceContainerHigh = Color(0xFFEAE3D7),
        surfaceContainerHighest = Color(0xFFE4DCCE),
    )

val KestrelDarkColors: ColorScheme =
    darkColorScheme(
        primary = DarkInk,
        onPrimary = Ink,
        primaryContainer = Color(0xFF3A352E),
        onPrimaryContainer = DarkInk,
        inversePrimary = Ink,
        secondary = DarkInkSoft,
        onSecondary = Color(0xFF2B2721),
        secondaryContainer = Color(0xFF37322B),
        onSecondaryContainer = DarkInk,
        tertiary = Color(0xFFA9C6E4),
        onTertiary = Color(0xFF0E2A44),
        tertiaryContainer = Color(0xFF1F3348),
        onTertiaryContainer = Color(0xFFD6E6F6),
        background = DarkPage,
        onBackground = DarkInk,
        surface = DarkPage,
        onSurface = DarkInk,
        surfaceVariant = Color(0xFF37322B),
        onSurfaceVariant = DarkInkSoft,
        surfaceTint = DarkInkSoft,
        inverseSurface = DarkInk,
        inverseOnSurface = Color(0xFF2F2B25),
        error = Color(0xFFFFB4A8),
        onError = Color(0xFF5C130E),
        errorContainer = Color(0xFF6B1D16),
        onErrorContainer = Color(0xFFFFDAD4),
        outline = Color(0xFF8F8678),
        outlineVariant = Color(0xFF3F3A33),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3A362F),
        surfaceDim = DarkPage,
        surfaceContainerLowest = Color(0xFF100E0B),
        surfaceContainerLow = Color(0xFF1C1915),
        surfaceContainer = Color(0xFF211E19),
        surfaceContainerHigh = Color(0xFF2B2722),
        surfaceContainerHighest = Color(0xFF36322C),
    )

/**
 * Catalogue colours the Material scheme has no role for.
 *
 * - [card]: lot cards, segments and receipt cards, one tonal step above the page.
 * - [plate]: the paper a lot photograph is printed on. Studio photos are multiplied onto it, so their white
 *   backgrounds become the plate. The dark plate is dimmer (#B0A898), so photos never outshine the text.
 * - [rule]: hairlines, the headroom track and the dock's top rule (the same value as outlineVariant).
 * - [inkSoft]: secondary text (the same value as onSurfaceVariant), named for the catalogue drawing code.
 * - [scenePhotoDim]: the multiply colour that draws scene photos at 88 % brightness in dark theme. Light theme leaves
 *   them at full brightness.
 */
@Immutable
data class CatalogueColors(
    val card: Color,
    val plate: Color,
    val rule: Color,
    val inkSoft: Color,
    val scenePhotoDim: Color,
)

val LightCatalogueColors =
    CatalogueColors(
        card = PaperCard,
        plate = Color(0xFFEEE7DA),
        rule = Color(0xFFD9D0C1),
        inkSoft = InkSoft,
        scenePhotoDim = Color.White,
    )

val DarkCatalogueColors =
    CatalogueColors(
        card = Color(0xFF1E1B17),
        plate = Color(0xFFB0A898),
        rule = Color(0xFF3F3A33),
        inkSoft = DarkInkSoft,
        scenePhotoDim = Color(0xFFE0E0E0),
    )

val LocalCatalogueColors = staticCompositionLocalOf { LightCatalogueColors }
