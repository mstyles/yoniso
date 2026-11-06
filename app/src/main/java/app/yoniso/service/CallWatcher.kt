package app.yoniso.service

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi

/**
 * Notices when a phone call starts ringing, so the overlay never hides the Answer / Decline
 * controls. On screen, an incoming call is a heads-up notification in system UI, which the
 * accessibility overlay sits above.
 *
 * Needs no permission:
 * - Android 12+: the audio mode changes to ringtone / in-call (also covers most VoIP apps).
 * - Android 8–11: the call state from [TelephonyManager], which was readable without a
 *   permission before Android 12 (carrier calls only).
 *
 * Must be started and stopped on the main thread.
 */
internal class CallWatcher(private val context: Context, private val onCall: () -> Unit) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var stopListening: (() -> Unit)? = null

    /** A call is ringing or in progress right now. */
    fun isCallActive(): Boolean = isCallMode(audioManager.mode)

    fun start() {
        if (stopListening != null) return
        stopListening = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) watchAudioMode() else watchCallState()
    }

    fun stop() {
        stopListening?.invoke()
        stopListening = null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun watchAudioMode(): () -> Unit {
        val listener = AudioManager.OnModeChangedListener { mode -> if (isCallMode(mode)) onCall() }
        audioManager.addOnModeChangedListener(context.mainExecutor, listener)
        return { audioManager.removeOnModeChangedListener(listener) }
    }

    @Suppress("DEPRECATION") // TelephonyCallback needs READ_PHONE_STATE for call state.
    private fun watchCallState(): () -> Unit {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return {}
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                if (state != TelephonyManager.CALL_STATE_IDLE) onCall()
            }
        }
        telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        return { telephony.listen(listener, PhoneStateListener.LISTEN_NONE) }
    }

    /** Anything other than normal: ringing, in a call, in a VoIP call, call screening. */
    private fun isCallMode(mode: Int): Boolean = mode > AudioManager.MODE_NORMAL
}
