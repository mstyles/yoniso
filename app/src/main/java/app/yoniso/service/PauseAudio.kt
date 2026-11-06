package app.yoniso.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.core.content.edit

/**
 * Keeps the app underneath quiet while the pause is showing. Yoniso plays no sound itself.
 *
 * 1. It holds temporary audio focus, the standard signal that something else needs the
 *    speaker for a moment. Apps that honour it (most media apps) pause and later resume.
 * 2. It mutes the media stream, because many games never request audio focus and would
 *    otherwise keep playing. The stream is only unmuted again if Yoniso muted it.
 *
 * A flag written *before* muting records that Yoniso muted the stream, so [restoreIfInterrupted]
 * can unmute it if the process died mid-pause. It is a synchronous SharedPreferences commit
 * because it must be on disk before the mute happens.
 */
internal class PauseAudio(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("pause_audio", Context.MODE_PRIVATE)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private var focusHeld = false

    /** Unmutes media if a previous run muted it and never got to restore it. */
    fun restoreIfInterrupted() {
        if (prefs.getBoolean(KEY_MUTED_BY_US, false)) unmute()
    }

    fun acquire() {
        if (!focusHeld) {
            focusHeld = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        // Already muted (by the user, or by us): leave it alone, so we never unmute what we didn't mute.
        if (!audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
            prefs.edit(commit = true) { putBoolean(KEY_MUTED_BY_US, true) }
            adjustMusic(AudioManager.ADJUST_MUTE)
        }
    }

    /** Safe to call when nothing is held. */
    fun release() {
        if (prefs.getBoolean(KEY_MUTED_BY_US, false)) unmute()
        if (focusHeld) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            focusHeld = false
        }
    }

    private fun unmute() {
        // If the user already unmuted during the pause, there is nothing to undo.
        if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) adjustMusic(AudioManager.ADJUST_UNMUTE)
        prefs.edit(commit = true) { remove(KEY_MUTED_BY_US) }
    }

    private fun adjustMusic(direction: Int) {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        } catch (e: SecurityException) {
            // E.g. Do Not Disturb policy. The pause still works, just not silenced.
            Log.w(TAG, "Could not change media mute", e)
        }
    }

    private companion object {
        const val TAG = "Yoniso"
        const val KEY_MUTED_BY_US = "muted_by_us"
    }
}
