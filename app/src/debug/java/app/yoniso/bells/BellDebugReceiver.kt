package app.yoniso.bells

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.yoniso.core.BellOutput
import app.yoniso.core.BellSettings
import app.yoniso.core.BellTiming
import app.yoniso.core.DuringMedia
import app.yoniso.core.VibrationPreset
import app.yoniso.data.BellRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * Debug builds only. Drives the bells over adb until the bells screen exists:
 *
 *     adb shell am broadcast -n app.yoniso/.bells.BellDebugReceiver --es cmd <cmd> [extras]
 *
 * - `set`: changes settings, then reschedules. Any of: `--ez enabled true`, `--es timing interval|random`,
 *   `--ei interval 5`, `--ei min 5 --ei max 10`, `--es start 08:00 --es end 21:00`,
 *   `--es days MONDAY,TUESDAY` (or `all`), `--es output bell|vibrate|both`, `--ef volume 0.5`,
 *   `--es media vibrate|ring|skip`, `--es vibration double_tap` (a preset), `--es bell plum_village`,
 *   `--ei snooze 60` (minutes, 0 to clear).
 * - `test`: sets the alarm `--ei delay 30` seconds from now (default 30), whatever the schedule says.
 * - `status`: logs the settings, the next ring and whether alarms are exact.
 *
 * Results go to `adb logcat -s Yoniso`.
 */
class BellDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        BellAlarms.scope.launch {
            try {
                handle(app, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Bell debug command failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val repository = BellRepository(context)
        when (intent.getStringExtra("cmd")) {
            "set" -> {
                repository.update { applyExtras(it, intent) }
                BellAlarms.reschedule(context)
            }
            "test" -> BellAlarms.setForTest(context, Instant.now().plusSeconds(intent.getIntExtra("delay", 30).toLong()))
            "status" -> Unit
            else -> Log.w(TAG, "Unknown bell debug command; use set, test or status")
        }
        Log.i(TAG, "Bells: ${repository.settings.first()}")
        Log.i(TAG, "Next bell: ${repository.nextRingAt()?.let { it.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }}, " +
            "exact alarms allowed: ${BellAlarms.canRingOnTime(context)}, accessibility connected: ${RunningState.accessibilityConnected}")
    }

    private fun applyExtras(current: BellSettings, intent: Intent): BellSettings {
        var s = current
        if (intent.hasExtra("enabled")) s = s.copy(enabled = intent.getBooleanExtra("enabled", false))
        intent.getStringExtra("timing")?.let { s = s.copy(timing = BellTiming.valueOf(it.uppercase())) }
        if (intent.hasExtra("interval")) s = s.copy(intervalMinutes = intent.getIntExtra("interval", 15))
        if (intent.hasExtra("min")) s = s.copy(randomMinMinutes = intent.getIntExtra("min", 10))
        if (intent.hasExtra("max")) s = s.copy(randomMaxMinutes = intent.getIntExtra("max", 30))
        intent.getStringExtra("start")?.let { s = s.copy(activeHours = s.activeHours.copy(start = LocalTime.parse(it))) }
        intent.getStringExtra("end")?.let { s = s.copy(activeHours = s.activeHours.copy(end = LocalTime.parse(it))) }
        intent.getStringExtra("days")?.let { days ->
            val set = if (days == "all") DayOfWeek.entries.toSet() else days.split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it.trim().uppercase()) }.toSet()
            s = s.copy(activeHours = s.activeHours.copy(days = set))
        }
        intent.getStringExtra("output")?.let { s = s.copy(output = BellOutput.valueOf(it.uppercase())) }
        if (intent.hasExtra("volume")) s = s.copy(volume = intent.getFloatExtra("volume", 0.5f))
        intent.getStringExtra("media")?.let { s = s.copy(duringMedia = DuringMedia.valueOf(it.uppercase())) }
        intent.getStringExtra("vibration")?.let { s = s.copy(vibration = VibrationPreset.valueOf(it.uppercase()).pattern) }
        intent.getStringExtra("bell")?.let { s = s.copy(bellId = it) }
        if (intent.hasExtra("snooze")) {
            val minutes = intent.getIntExtra("snooze", 0)
            s = s.copy(snoozedUntil = if (minutes > 0) Instant.now().plusSeconds(minutes * 60L) else null)
        }
        return s
    }

    private companion object {
        const val TAG = "Yoniso"
    }
}
