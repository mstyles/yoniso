package app.yoniso.bells

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import app.yoniso.bells.BellAlarms.debugLog
import app.yoniso.core.BellSchedule
import app.yoniso.core.PhoneState
import app.yoniso.core.RingDecider
import app.yoniso.core.RingDecision
import app.yoniso.core.RingerMode
import app.yoniso.data.BellRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * The bell's alarm fires here. It sets the next alarm, then decides with [RingDecider] whether this
 * bell rings, vibrates or is skipped.
 *
 * Vibration plays straight from here. The bell lasts longer than a receiver may run, so it plays in
 * [BellService]. If Android refuses to start that service from the background (an inexact alarm with
 * the accessibility service off), the bell vibrates instead.
 */
class BellReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        BellAlarms.scope.launch {
            try {
                ring(app, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun ring(context: Context, intent: Intent) {
        val now = Instant.now()
        val due = intent.getLongExtra(EXTRA_DUE, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli) ?: now
        val setAt = intent.getLongExtra(EXTRA_SET_AT, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli) ?: due
        val exact = intent.getBooleanExtra(EXTRA_EXACT, false)
        val settings = BellRepository(context).settings.first()

        // Set the next alarm first, so nothing that goes wrong below can stop the bells.
        BellAlarms.reschedule(context, after = maxOf(now, due))

        val phone = phoneState(context, canPlaySound = exact || RunningState.accessibilityConnected)
        val decision = RingDecider.decide(
            due = due,
            now = now,
            settings = settings,
            zone = ZoneId.systemDefault(),
            phone = phone,
            lateTolerance = BellSchedule.lateTolerance(setAt, due, exact),
        )
        debugLog { "Bell due ${due.atZone(ZoneId.systemDefault()).toLocalTime()}, ${now.toEpochMilli() - due.toEpochMilli()}ms late, exact=$exact: $phone -> $decision" }
        if (decision !is RingDecision.Ring) return

        var vibrate = decision.vibrate
        if (decision.sound && !startBell(context, settings.bellId, settings.volume)) vibrate = true
        if (vibrate) BellVibrator.vibrate(context, settings.vibration)
    }

    /** Returns false if Android refused to start the service. */
    private fun startBell(context: Context, bellId: String, volume: Float): Boolean = try {
        BellService.start(context, bellId, volume)
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (Android 12+) is an IllegalStateException.
        val refused = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException
        Log.w(BellAlarms.TAG, if (refused) "Not allowed to start the bell; vibrating instead" else "Could not start the bell", e)
        false
    }

    private fun phoneState(context: Context, canPlaySound: Boolean): PhoneState {
        val audio = context.getSystemService(AudioManager::class.java)
        val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return PhoneState(
            ringerMode = when (audio.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
                AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
                else -> RingerMode.NORMAL
            },
            doNotDisturb = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            // Ringing, in a call, in a VoIP call, call screening: anything but normal.
            inCall = audio.mode > AudioManager.MODE_NORMAL,
            mediaPlaying = audio.isMusicActive,
            pauseShowing = RunningState.pauseShowing,
            canPlaySound = canPlaySound,
        )
    }

    internal companion object {
        private const val EXTRA_DUE = "due"
        private const val EXTRA_SET_AT = "set_at"
        private const val EXTRA_EXACT = "exact"

        /** The alarm's intent: when the bell is [due], when the alarm was set, and whether it is exact. */
        fun intent(context: Context, due: Instant, setAt: Instant, exact: Boolean): Intent =
            Intent(context, BellReceiver::class.java)
                .putExtra(EXTRA_DUE, due.toEpochMilli())
                .putExtra(EXTRA_SET_AT, setAt.toEpochMilli())
                .putExtra(EXTRA_EXACT, exact)
    }
}
