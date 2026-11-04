package app.kestrel.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.kestrel.R
import app.kestrel.model.StatusFamily

/**
 * The six status families of DESIGN 2.2, plus [Failed]: the failure tone of the Ended family and the only red in
 * the app. Every tone is shown as colour, glyph and words together, never colour alone. The tones never change with
 * the theme seed or the wallpaper.
 */
enum class StatusTone(
    @param:DrawableRes val icon: Int,
) {
    Watching(R.drawable.ic_visibility),
    Armed(R.drawable.ic_schedule),
    Attention(R.drawable.ic_warning_fill),
    Bidding(R.drawable.ic_gavel),
    Won(R.drawable.ic_trophy_fill),
    Ended(R.drawable.ic_flag),
    Failed(R.drawable.ic_error_fill),
    ;

    companion object {
        /** The tone of a domain family; [failure] picks the red tone inside the Ended family. */
        fun of(
            family: StatusFamily,
            failure: Boolean = false,
        ): StatusTone =
            when (family) {
                StatusFamily.WATCHING -> Watching
                StatusFamily.ARMED -> Armed
                StatusFamily.ATTENTION -> Attention
                StatusFamily.BIDDING -> Bidding
                StatusFamily.WON -> Won
                StatusFamily.ENDED -> if (failure) Failed else Ended
            }
    }
}

/** [accent] is for glyphs and words on paper, [container] for soft fills, [onContainer] for text on that fill. */
@Immutable
data class ToneColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

@Immutable
data class StatusPalette(
    val watching: ToneColors,
    val armed: ToneColors,
    val attention: ToneColors,
    val bidding: ToneColors,
    val won: ToneColors,
    val ended: ToneColors,
    val failed: ToneColors,
) {
    operator fun get(tone: StatusTone): ToneColors =
        when (tone) {
            StatusTone.Watching -> watching
            StatusTone.Armed -> armed
            StatusTone.Attention -> attention
            StatusTone.Bidding -> bidding
            StatusTone.Won -> won
            StatusTone.Ended -> ended
            StatusTone.Failed -> failed
        }
}

val LightStatusPalette =
    StatusPalette(
        watching = ToneColors(Color(0xFF5E5446), Color(0xFFECE5D9), Color(0xFF3A3329)),
        armed = ToneColors(Color(0xFF2D5275), Color(0xFFDEE7F0), Color(0xFF16324D)),
        attention = ToneColors(Color(0xFF8A5200), Color(0xFFF7E6C6), Color(0xFF4D2E00)),
        bidding = ToneColors(Color(0xFF5A3D92), Color(0xFFE8E0F5), Color(0xFF2F1C57)),
        won = ToneColors(Color(0xFF2D6A40), Color(0xFFDAEEDD), Color(0xFF133A20)),
        ended = ToneColors(Color(0xFF67625A), Color(0xFFE7E3DC), Color(0xFF3B3732)),
        failed = ToneColors(Color(0xFFA3261E), Color(0xFFF9DEDA), Color(0xFF5C130E)),
    )

val DarkStatusPalette =
    StatusPalette(
        watching = ToneColors(Color(0xFFD2C7B5), Color(0xFF34302A), Color(0xFFEDE6D9)),
        armed = ToneColors(Color(0xFFA9C6E4), Color(0xFF1F3348), Color(0xFFD6E6F6)),
        attention = ToneColors(Color(0xFFF0BE6A), Color(0xFF45310F), Color(0xFFFCE3B8)),
        bidding = ToneColors(Color(0xFFCDBAF3), Color(0xFF362A52), Color(0xFFEADFFF)),
        won = ToneColors(Color(0xFF96D0A3), Color(0xFF1C3925), Color(0xFFCDEFD4)),
        ended = ToneColors(Color(0xFFBDB7AD), Color(0xFF2E2B27), Color(0xFFE7E2DA)),
        failed = ToneColors(Color(0xFFFFB4A8), Color(0xFF6B1D16), Color(0xFFFFDAD4)),
    )

val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }
