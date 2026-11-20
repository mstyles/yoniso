package app.yoniso.bells

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.annotation.RawRes
import app.yoniso.R
import app.yoniso.core.BellSettings
import app.yoniso.core.CustomBell

/**
 * The bells that come with the app. Settings store [id], never the resource number, so the order and
 * resources can change. [author] and [sourceUrl] credit each sound on the About screen.
 *
 * The Freesound bells are CC0 1.0 (public domain). Each was trimmed to one strike, faded out and
 * normalised to about -20.5 LUFS, like the Plum Village bell.
 */
enum class BundledBell(
    val id: String,
    val label: String,
    @param:RawRes val resId: Int,
    val author: String,
    val sourceUrl: String?,
) {
    /** From the Plum Village app, used with permission. */
    PLUM_VILLAGE(BellSettings.DEFAULT_BELL_ID, "Plum Village bell", R.raw.bell, "Plum Village", null),
    SINGING_BOWL(
        "singing_bowl", "Singing bowl", R.raw.bell_singing_bowl,
        "the_very_Real_Horst", "https://freesound.org/people/the_very_Real_Horst/sounds/240934/",
    ),
    DEEP_BOWL(
        "deep_bowl", "Deep bowl", R.raw.bell_deep_bowl,
        "dersinnsspace", "https://freesound.org/people/dersinnsspace/sounds/421829/",
    ),
    BRIGHT_BELL(
        "bright_bell", "Bright bell", R.raw.bell_bright_bell,
        "steaq", "https://freesound.org/people/steaq/sounds/346328/",
    ),
    TINGSHA(
        "tingsha", "Tingsha", R.raw.bell_tingsha,
        "steffcaffrey", "https://freesound.org/people/steffcaffrey/sounds/435074/",
    ),
    HARMONY_BELL(
        "harmony_bell", "Harmony bell", R.raw.bell_harmony_bell,
        "newagesoup", "https://freesound.org/people/newagesoup/sounds/400605/",
    ),
    TEMPLE_BELL(
        "temple_bell", "Temple bell", R.raw.bell_temple_bell,
        "tec_studio", "https://freesound.org/people/tec_studio/sounds/668647/",
    );

    companion object {
        val DEFAULT = PLUM_VILLAGE

        fun of(id: String): BundledBell? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Plays one bell, for the pause's "Listen to a bell" and for the mindfulness bells.
 *
 * It plays on the alarm stream: [app.yoniso.service.PauseAudio] mutes the media stream during the
 * pause, and the notification stream is muted in vibrate mode. The bell's loudness follows the
 * phone's alarm volume, times the volume given to [play]. The caller decides whether it should ring
 * at all (silent mode and Do Not Disturb don't affect the alarm stream).
 *
 * A custom bell plays its copied file. An unknown bell, a missing file, or one that fails to load
 * or play falls back to [BundledBell.DEFAULT], and the error is logged.
 *
 * Must be used on the main thread.
 */
internal class BellPlayer(private val context: Context) {
    private val customFiles = CustomBellFiles(context)
    private var player: MediaPlayer? = null
    private var finishCallback: (() -> Unit)? = null

    /** Starts the bell. [onFinished] runs once, when it rings out, fails, or [stop] cuts it short. */
    fun play(bellId: String, volume: Float = 1f, onFinished: () -> Unit) {
        stop()
        finishCallback = onFinished
        val v = volume.coerceIn(0f, 1f)
        val bundled = BundledBell.of(bellId)
        when {
            bundled != null -> start(bundled, v)
            CustomBell.fileNameOf(bellId) != null -> {
                val file = customFiles.fileOf(bellId)
                if (file != null) {
                    start(bellId, v) { MediaPlayer.create(context, Uri.fromFile(file), null, ATTRIBUTES, 0) }
                } else {
                    Log.w(TAG, "Custom bell $bellId has no file")
                    start(BundledBell.DEFAULT, v)
                }
            }
            else -> {
                Log.w(TAG, "Unknown bell $bellId")
                start(BundledBell.DEFAULT, v)
            }
        }
    }

    private fun start(bell: BundledBell, volume: Float) =
        start(bell.id, volume) { MediaPlayer.create(context, bell.resId, ATTRIBUTES, 0) }

    private fun start(id: String, volume: Float, create: () -> MediaPlayer?) {
        val created = try {
            create()
        } catch (e: Exception) {
            Log.w(TAG, "Could not load bell $id", e)
            null
        }
        if (created == null) {
            Log.w(TAG, "Could not load bell $id")
            fallBackFrom(id, volume)
            return
        }
        player = created
        created.setVolume(volume, volume)
        created.setOnCompletionListener { stop() }
        created.setOnErrorListener { mp, what, extra ->
            Log.w(TAG, "Bell $id playback error $what/$extra")
            if (player === mp) {
                player = null
                mp.release()
                fallBackFrom(id, volume)
            }
            true
        }
        created.start()
    }

    private fun fallBackFrom(id: String, volume: Float) {
        if (id != BundledBell.DEFAULT.id) start(BundledBell.DEFAULT, volume) else stop()
    }

    /** Safe to call when nothing is playing. */
    fun stop() {
        player?.let {
            player = null
            it.release()
        }
        finishCallback?.let {
            finishCallback = null
            it()
        }
    }

    companion object {
        private const val TAG = "Yoniso"

        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
