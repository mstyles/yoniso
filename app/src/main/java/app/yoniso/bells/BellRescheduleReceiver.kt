package app.yoniso.bells

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.yoniso.bells.BellAlarms.debugLog
import kotlinx.coroutines.launch

/**
 * Sets the bell's alarm again when Android may have lost it or its time has moved:
 * - Reboot, app update, exact alarms allowed: the same ring time is set again ([BellAlarms.restore]).
 * - Clock or time zone changed: the next ring is worked out afresh ([BellAlarms.reschedule]).
 */
class BellRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in RESTORE && action !in RESCHEDULE) return
        debugLog { "Rescheduling bells: $action" }
        val pending = goAsync()
        val app = context.applicationContext
        BellAlarms.scope.launch {
            try {
                if (action in RESTORE) BellAlarms.restore(app) else BellAlarms.reschedule(app)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        // The exact-alarm action is a string copied in at compile time; before Android 12 it is never sent.
        @SuppressLint("InlinedApi")
        val RESTORE = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        val RESCHEDULE = setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    }
}
