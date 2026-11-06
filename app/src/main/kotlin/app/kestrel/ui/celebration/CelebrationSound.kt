package app.kestrel.ui.celebration

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.runtime.staticCompositionLocalOf
import app.kestrel.R

/**
 * The win celebration's sound: one track (res/raw/celebration.wav, made by tools/sound/celebration_sound.py) timed to
 * the scene, from the wind and the stoop to the strike, the bells, the "kee-kee-kee" and the meadow's plucks.
 */
interface CelebrationSound {
    /** Starts the track from the beginning. */
    fun play()

    /** Stops it at once (skipped, closed or played again) and lets go of the player. */
    fun stop()
}

/**
 * Makes the sound for one celebration, or null for silence (the sound is off in Settings, or a test or screenshot
 * draws the scene). The app provides it from Settings; everywhere else the celebration is silent.
 */
val LocalCelebrationSound = staticCompositionLocalOf<() -> CelebrationSound?> { { null } }

/**
 * Plays the track at media volume, as a sound effect, and asks other audio (music, a podcast) to duck for it rather
 * than stop. Nothing happens if the player cannot be made: the celebration simply stays silent.
 */
class AndroidCelebrationSound(
    context: Context,
) : CelebrationSound {
    private val context = context.applicationContext
    private val audio = this.context.getSystemService(AudioManager::class.java)
    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    private val focus =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
    private var player: MediaPlayer? = null

    override fun play() {
        stop()
        val session = audio?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE
        val made =
            runCatching { MediaPlayer.create(context, R.raw.celebration, attributes, session) }.getOrNull() ?: return
        player = made
        audio?.requestAudioFocus(focus)
        made.setOnCompletionListener { stop() }
        runCatching { made.start() }.onFailure { stop() }
    }

    override fun stop() {
        val playing = player ?: return
        player = null
        runCatching { playing.stop() }
        playing.release()
        audio?.abandonAudioFocusRequest(focus)
    }
}
