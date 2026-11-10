package app.yoniso.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import app.yoniso.R

/**
 * Plays the bell (res/raw/bell.mp3, from Plum Village, used with permission) when asked from the pause.
 *
 * It plays on the alarm stream, not media: [PauseAudio] mutes the media stream during the pause,
 * which would silence a media bell too. The alarm stream also plays when the phone is on vibrate or
 * silent, which is right here because the user has just asked to hear the bell. Its loudness follows
 * the phone's alarm volume.
 *
 * Must be used on the main thread.
 */
internal class PauseBell(private val context: Context) {
    private var player: MediaPlayer? = null
    private var finishCallback: (() -> Unit)? = null

    /** Starts the bell. [onFinished] runs when it rings out, fails, or [stop] cuts it short. */
    fun play(onFinished: () -> Unit) {
        stop()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val created = MediaPlayer.create(context, R.raw.bell, attributes, 0)
        if (created == null) {
            Log.w(TAG, "Could not load the bell")
            onFinished()
            return
        }
        player = created
        finishCallback = onFinished
        created.setOnCompletionListener { stop() }
        created.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "Bell playback error $what/$extra")
            stop()
            true
        }
        created.start()
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

    private companion object {
        const val TAG = "Yoniso"
    }
}
