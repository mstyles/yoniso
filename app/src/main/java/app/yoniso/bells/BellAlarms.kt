package app.yoniso.bells

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import app.yoniso.BuildConfig
import app.yoniso.core.BellSchedule
import app.yoniso.data.BellRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * Sets the one alarm for the next bell. Only the next ring is ever set: when it fires, [BellReceiver]
 * sets the one after. The time is also stored, so it can be set again after a reboot.
 *
 * The alarm is exact (`setExactAndAllowWhileIdle`) when the user has allowed exact alarms. Only an
 * exact alarm lets Yoniso start the bell service from the background when the accessibility service
 * is off. Otherwise it is inexact (`setAndAllowWhileIdle`), which Android may deliver late, and in
 * Doze at most about once every 9 minutes.
 */
internal object BellAlarms {
    const val TAG = "Yoniso"

    /** For work that outlives its caller: receivers (with `goAsync`) and the service's connection. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Scheduling reads, then writes, the stored next ring; one at a time keeps them consistent. */
    private val lock = Mutex()

    /**
     * Works out the first ring after [after] and sets its alarm, or cancels it if bells are off.
     * For any settings change, a clock or time zone change, and after each bell.
     */
    suspend fun reschedule(context: Context, after: Instant = Instant.now()) = lock.withLock {
        val settings = BellRepository(context).settings.first()
        set(context, BellSchedule.nextRing(after, settings, ZoneId.systemDefault(), Random.Default))
    }

    /**
     * Sets the alarm for the stored next ring again, or a new one if it has passed (a missed bell is
     * not rung late). For a reboot, an app update, exact alarms being allowed, and the app starting,
     * none of which should move a random bell that is already chosen.
     */
    suspend fun restore(context: Context) {
        val repository = BellRepository(context)
        val stored = lock.withLock { repository.nextRingAt() }
        val now = Instant.now()
        if (stored != null && stored > now && repository.settings.first().enabled) {
            lock.withLock { set(context, stored) }
        } else {
            reschedule(context, now)
        }
    }

    /** [restore] without waiting, from code that can't suspend. */
    fun restoreAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { restore(app) }
    }

    /** Debug builds: sets the alarm for [at] directly, whatever the schedule says. */
    suspend fun setForTest(context: Context, at: Instant) = lock.withLock { set(context, at) }

    /** Whether alarms are set exactly. Always true before Android 12. */
    fun canRingOnTime(context: Context): Boolean =
        AlarmManagerCompat.canScheduleExactAlarms(context.getSystemService(AlarmManager::class.java))

    private suspend fun set(context: Context, at: Instant?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val repository = BellRepository(context)
        if (at == null) {
            alarmManager.cancel(pendingIntent(context, BellReceiver.intent(context, due = Instant.EPOCH, setAt = Instant.EPOCH, exact = false)))
            repository.setNextRingAt(null)
            debugLog { "No next bell" }
            return
        }
        val now = Instant.now()
        var exact = canRingOnTime(context)
        val millis = at.toEpochMilli()
        if (exact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    millis,
                    pendingIntent(context, BellReceiver.intent(context, at, now, exact = true)),
                )
            } catch (e: SecurityException) {
                // Revoked between the check and the call.
                Log.w(TAG, "Exact alarm refused", e)
                exact = false
            }
        }
        if (!exact) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                millis,
                pendingIntent(context, BellReceiver.intent(context, at, now, exact = false)),
            )
        }
        repository.setNextRingAt(at)
        debugLog { "Next bell at ${at.atZone(ZoneId.systemDefault()).toLocalDateTime()} (exact=$exact)" }
    }

    /** One request code and one intent target, so each alarm replaces the last. */
    private fun pendingIntent(context: Context, intent: Intent): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }
}
