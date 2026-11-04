package app.kestrel.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// DESIGN 2.7. Cards and plates are gently rounded like trimmed card stock; buttons and chips are full pills with the
// Expressive press morph (ButtonDefaults.shapes()), so actions never look like content.
val KestrelShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** Plate corners and sizes from DESIGN 2.4. Plates are lot photographs printed on paper. */
object PlateShapes {
    /** Rows (72 dp) and the S2 header (104 dp). */
    val Row = RoundedCornerShape(10.dp)

    /** The hero plate is the top of the first segment: 20 dp above, 4 dp where the card body joins it. */
    val Hero = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)

    /** S4 result plates. */
    val Result = RoundedCornerShape(20.dp)

    /** The 40 dp plate in sheets. */
    val Sheet = RoundedCornerShape(8.dp)
}

/** The 28 dp top corners of bottom sheets and the S3 sheet over the photo. */
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** The dock's readiness pill. */
val ReadinessPillShape = RoundedCornerShape(20.dp)
