package app.kestrel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kestrel.R

// DESIGN 2.5. Newsreader (variable serif: wght 200-800, opsz 6-72) sets titles, prices and countdowns like the entries
// of a printed catalogue; Google Sans Flex (variable: wght, wdth, opsz, ROND, slnt) sets every other word. Each cut is
// a family with one Font per weight and its axes pinned, because Android does not apply the optical size by itself.

/** OpenType feature for tabular figures. Only serif figure styles carry it in their TextStyle (rules T1 and T2). */
const val TABULAR_FIGURES = "tnum"

private fun serif(
    weight: Int,
    opticalSize: Float,
) = Font(
    resId = R.font.newsreader,
    weight = FontWeight(weight),
    variationSettings =
        FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("opsz", opticalSize),
        ),
)

private fun sans(weight: Int) =
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight(weight),
        variationSettings =
            FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(100f),
                FontVariation.Setting("opsz", 16f),
                FontVariation.Setting("ROND", 0f),
            ),
    )

/** Newsreader at opsz 60: the countdown, hero prices and result headlines. */
val SerifDisplay = FontFamily(serif(400, 60f), serif(500, 60f))

/** Newsreader at opsz 28: screen titles, lot titles and large prices. */
val SerifHeadline = FontFamily(serif(400, 28f), serif(500, 28f), serif(600, 28f))

/** Newsreader at opsz 16: row titles, row prices, eBay's quoted reply and section heads. */
val SerifText = FontFamily(serif(500, 16f), serif(600, 16f), serif(700, 16f))

/** Google Sans Flex at wdth 100, ROND 0, opsz 16: all UI text. */
val Sans = FontFamily(sans(400), sans(500), sans(600), sans(700))

private val TrimBoth =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

private fun style(
    family: FontFamily,
    weight: Int,
    size: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight(weight),
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
)

/** The same style with tabular figures on the whole string: serif figure styles and figures-only text. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR_FIGURES)

// Emphasized variants add 100 to the weight: serif 400 to 500 and 500 to 600, sans 400 to 500 and 600 to 700.
val KestrelTypography =
    Typography(
        displayLarge = style(SerifDisplay, 400, 72.sp, 76.sp, (-1.5).sp),
        displayMedium = style(SerifDisplay, 400, 52.sp, 56.sp, (-0.8).sp),
        displaySmall = style(SerifDisplay, 400, 40.sp, 46.sp, (-0.4).sp),
        headlineLarge = style(SerifHeadline, 400, 34.sp, 40.sp, (-0.3).sp),
        headlineMedium = style(SerifHeadline, 400, 28.sp, 34.sp, (-0.2).sp),
        headlineSmall = style(SerifHeadline, 500, 24.sp, 30.sp, (-0.1).sp),
        titleLarge = style(SerifText, 500, 20.sp, 26.sp),
        titleMedium = style(Sans, 600, 16.sp, 22.sp, 0.1.sp),
        titleSmall = style(Sans, 600, 14.sp, 20.sp, 0.1.sp),
        bodyLarge = style(Sans, 400, 16.sp, 24.sp, 0.1.sp),
        bodyMedium = style(Sans, 400, 14.sp, 20.sp, 0.15.sp),
        bodySmall = style(Sans, 400, 12.sp, 16.sp, 0.25.sp),
        labelLarge = style(Sans, 600, 14.sp, 20.sp, 0.1.sp),
        labelMedium = style(Sans, 600, 12.sp, 16.sp, 0.3.sp),
        labelSmall = style(Sans, 600, 11.sp, 16.sp, 0.4.sp),
        displayLargeEmphasized = style(SerifDisplay, 500, 72.sp, 76.sp, (-1.5).sp),
        displayMediumEmphasized = style(SerifDisplay, 500, 52.sp, 56.sp, (-0.8).sp),
        displaySmallEmphasized = style(SerifDisplay, 500, 40.sp, 46.sp, (-0.4).sp),
        headlineLargeEmphasized = style(SerifHeadline, 500, 34.sp, 40.sp, (-0.3).sp),
        headlineMediumEmphasized = style(SerifHeadline, 500, 28.sp, 34.sp, (-0.2).sp),
        headlineSmallEmphasized = style(SerifHeadline, 600, 24.sp, 30.sp, (-0.1).sp),
        titleLargeEmphasized = style(SerifText, 600, 20.sp, 26.sp),
        titleMediumEmphasized = style(Sans, 700, 16.sp, 22.sp, 0.1.sp),
        titleSmallEmphasized = style(Sans, 700, 14.sp, 20.sp, 0.1.sp),
        bodyLargeEmphasized = style(Sans, 500, 16.sp, 24.sp, 0.1.sp),
        bodyMediumEmphasized = style(Sans, 500, 14.sp, 20.sp, 0.15.sp),
        bodySmallEmphasized = style(Sans, 500, 12.sp, 16.sp, 0.25.sp),
        labelLargeEmphasized = style(Sans, 700, 14.sp, 20.sp, 0.1.sp),
        labelMediumEmphasized = style(Sans, 700, 12.sp, 16.sp, 0.3.sp),
        labelSmallEmphasized = style(Sans, 700, 11.sp, 16.sp, 0.4.sp),
    )

/**
 * Catalogue styles with no Material role (DESIGN 2.5). The serif figure styles ([countdown], [countdownMedium],
 * [priceHero], [priceLarge], [priceRow]) carry tabular figures and only ever get figure strings, never words.
 * [sectionHead] and [smallCaps] are set in capitals by the components that use them.
 */
@Immutable
data class CatalogueType(
    val countdown: TextStyle,
    val countdownUnit: TextStyle,
    val countdownMedium: TextStyle,
    val priceHero: TextStyle,
    val priceLarge: TextStyle,
    val priceRow: TextStyle,
    val priceRowTotal: TextStyle,
    val lotTitle: TextStyle,
    val lotTitleSmall: TextStyle,
    val sectionHead: TextStyle,
    val smallCaps: TextStyle,
    val stateWord: TextStyle,
    val stateWordLarge: TextStyle,
)

val KestrelCatalogueType =
    CatalogueType(
        countdown =
            style(SerifDisplay, 400, 88.sp, 88.sp, (-2).sp)
                .tabular()
                .copy(lineHeightStyle = TrimBoth),
        countdownUnit = style(SerifHeadline, 400, 32.sp, 36.sp, (-0.2).sp),
        countdownMedium = style(SerifDisplay, 400, 40.sp, 44.sp, (-0.4).sp).tabular(),
        priceHero = style(SerifDisplay, 500, 40.sp, 44.sp, (-0.4).sp).tabular(),
        priceLarge = style(SerifHeadline, 500, 28.sp, 32.sp, (-0.2).sp).tabular(),
        priceRow = style(SerifText, 600, 21.sp, 26.sp).tabular(),
        priceRowTotal = style(SerifText, 700, 21.sp, 26.sp).tabular(),
        lotTitle = style(SerifHeadline, 500, 22.sp, 27.sp, (-0.1).sp).copy(lineBreak = LineBreak.Heading),
        lotTitleSmall = style(SerifText, 500, 17.sp, 22.sp).copy(lineBreak = LineBreak.Heading),
        sectionHead = style(SerifText, 600, 13.sp, 16.sp, 0.16.em),
        smallCaps = style(Sans, 600, 11.sp, 16.sp, 0.12.em),
        stateWord = style(Sans, 700, 14.sp, 20.sp, 0.1.sp),
        stateWordLarge = style(Sans, 700, 16.sp, 22.sp, 0.1.sp),
    )

val LocalCatalogueType = staticCompositionLocalOf { KestrelCatalogueType }
