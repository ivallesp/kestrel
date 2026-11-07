package app.kestrel.ui.celebration

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import app.kestrel.R

/** The scenes' tracks, each timed to its scene and made by a script in tools/sound. */
enum class SceneTrack(
    val raw: Int,
) {
    /** The win: wind, the stoop, the strike, the bells, the "kee-kee-kee" and the meadow's plucks. */
    Win(R.raw.celebration),

    /** The loss: wind, the stoop, the empty strike and the gust, a falling phrase, the rain and one low "kee". */
    Loss(R.raw.loss),
}

/**
 * The sound of the win celebration and of the loss: one track at a time (res/raw/celebration.wav, made by
 * tools/sound/celebration_sound.py, and res/raw/loss.wav, made by tools/sound/loss_sound.py), timed to its scene.
 */
interface CelebrationSound {
    /** Starts [track] from the beginning. */
    fun play(track: SceneTrack = SceneTrack.Win)

    /** Stops it at once (skipped, closed or played again) and lets go of the player. */
    fun stop()

    /** The Easter egg: one short "kee-kee" (res/raw/kestrel_chirp.wav), over anything else playing. */
    fun chirp() {}

    /** Lets go of everything, when the celebration leaves the screen. */
    fun release() = stop()
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

    // The chirp is short and may come in quick taps, so it plays from a sound pool, loaded once.
    private val pool: SoundPool =
        SoundPool
            .Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
    private val chirpSound: Int = pool.load(this.context, R.raw.kestrel_chirp, 1)

    override fun play(track: SceneTrack) {
        stop()
        val session = audio?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE
        val made =
            runCatching { MediaPlayer.create(context, track.raw, attributes, session) }.getOrNull() ?: return
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

    override fun chirp() {
        // Before the pool has loaded it (a fraction of a second), a tap is simply silent.
        pool.play(chirpSound, 1f, 1f, 1, 0, 1f)
    }

    override fun release() {
        stop()
        pool.release()
    }
}
