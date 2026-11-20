package app.yoniso.bells

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.yoniso.core.VibrationPattern

/**
 * Plays a bell's vibration. The system vibrator service plays the whole waveform (at most six
 * seconds) on its own, so this returns at once and works from a receiver.
 *
 * Uses notification usage: the user's notification vibration settings apply, and Android plays it
 * in vibrate mode. (Silent mode and Do Not Disturb are skipped before getting here.)
 */
internal object BellVibrator {
    fun vibrate(context: Context, pattern: VibrationPattern) {
        val vibrator = vibratorOf(context)
        if (!vibrator.hasVibrator()) return
        val waveform = pattern.toWaveform(amplitudeControl = vibrator.hasAmplitudeControl())
        val effect = VibrationEffect.createWaveform(
            waveform.timings.toLongArray(),
            waveform.amplitudes.toIntArray(),
            -1,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }

    /** False on phones without a vibrator, where the vibration settings are hidden. */
    fun hasVibrator(context: Context): Boolean = vibratorOf(context).hasVibrator()

    /** Whether the strength can be set. Without it, intensity and swell are hidden. */
    fun hasAmplitudeControl(context: Context): Boolean = vibratorOf(context).hasAmplitudeControl()

    private fun vibratorOf(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
}
