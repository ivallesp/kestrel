package app.kestrel.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The 4 dp grid and the two columns of DESIGN 2.8. Column A (16 dp from the screen edge) holds screen titles,
 * section heads, card edges, dock content and free paragraphs; column B (32 dp) holds everything inside cards.
 * End edges are symmetric: cards end at 395 dp and their content at 379 dp on a 411 dp screen.
 */
object Spacing {
    val Hairline = 2.dp
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val ExtraLarge = 20.dp
    val Section = 24.dp
    val Double = 32.dp

    /** Column A: screen edge to titles, section heads and card edges. */
    val ColumnA = 16.dp

    /** Card edge to card content, which puts the content on column B (32 dp from the screen edge). */
    val CardInset = 16.dp

    /** Plate to text in rows. */
    val PlateToText = 12.dp

    /** Gap between blocks inside a section. */
    val BlockGap = 12.dp

    /** Space between the last list item and the dock. */
    val AboveDock = 24.dp

    /** Screens without a dock end this far above the navigation-bar inset. */
    val ScreenEnd = 32.dp

    /** Height of the page-colour fade above the gesture handle ([app.kestrel.ui.components.BottomFade]). */
    val BottomFadeHeight = 28.dp

    /** Minimum touch target; inline buttons are 40 dp tall visually inside it. */
    val TouchTarget = 48.dp
    val InlineButtonHeight = 40.dp
    val PrimaryButtonHeight = 56.dp
}
