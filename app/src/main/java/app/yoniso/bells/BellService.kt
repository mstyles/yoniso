package app.yoniso.bells

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.yoniso.R
import app.yoniso.bells.BellAlarms.debugLog

/**
 * Plays one mindfulness bell as a short foreground service, then stops. A receiver only has about
 * 10 seconds, and the bell lasts about 22.
 *
 * - Type `shortService` on Android 14+ (no extra permission, about a 3-minute limit). Before 14 it
 *   has no type: `shortService` doesn't exist there and none is needed. `mediaPlayback` is avoided
 *   because Play's declaration for it requires playback the user starts, which a bell isn't.
 * - Its notification is quiet, on a low-importance channel, with a Stop action. Without the
 *   notification permission (Android 13+) the service still runs; the notification just doesn't show.
 * - It takes transient audio focus that lets other audio duck, so music dips rather than stops.
 *
 * Android only lets it start from the background after an exact alarm, or while the accessibility
 * service is bound. [start] throws otherwise, and [BellReceiver] vibrates instead.
 */
class BellService : Service() {
    private val player by lazy { BellPlayer(this) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(BellPlayer.ATTRIBUTES)
        .build()
    private var focusHeld = false

    /** The latest start. An earlier bell finishing (cut short by a new one) mustn't stop the service. */
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_STOP || intent == null) {
            finish()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType())
        } catch (e: RuntimeException) {
            Log.w(BellAlarms.TAG, "Could not start the bell in the foreground", e)
            finish()
            return START_NOT_STICKY
        }
        if (!focusHeld) {
            focusHeld = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!focusHeld) Log.w(BellAlarms.TAG, "Bell audio focus not granted")
        }
        val bellId = intent.getStringExtra(EXTRA_BELL_ID) ?: BundledBell.DEFAULT.id
        val volume = intent.getFloatExtra(EXTRA_VOLUME, 1f)
        debugLog { "Ringing $bellId at volume $volume" }
        player.play(bellId, volume) { if (startId == latestStartId) finish() }
        return START_NOT_STICKY
    }

    /** `shortService` ran out of time (Android 14). Must stop within a few seconds. */
    override fun onTimeout(startId: Int) = finish()

    /** `shortService` ran out of time (Android 15+). */
    override fun onTimeout(startId: Int, fgsType: Int) = finish()

    override fun onDestroy() {
        player.stop()
        releaseFocus()
        super.onDestroy()
    }

    private fun finish() {
        player.stop()
        releaseFocus()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseFocus() {
        if (focusHeld) {
            audio.abandonAudioFocusRequest(focusRequest)
            focusHeld = false
        }
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0

    private fun notification() = NotificationCompat.Builder(this, ensureChannel())
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(getString(R.string.bell_notification_title))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setSilent(true)
        .setOngoing(true)
        .addAction(
            0,
            getString(R.string.bell_notification_stop),
            PendingIntent.getService(
                this,
                0,
                Intent(this, BellService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun ensureChannel(): String {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bell_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.bell_channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return CHANNEL_ID
    }

    internal companion object {
        private const val CHANNEL_ID = "bells"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "app.yoniso.bells.STOP"
        private const val EXTRA_BELL_ID = "bell_id"
        private const val EXTRA_VOLUME = "volume"

        /** Throws if Android doesn't allow a foreground service to start from the background now. */
        fun start(context: Context, bellId: String, volume: Float) {
            val intent = Intent(context, BellService::class.java)
                .putExtra(EXTRA_BELL_ID, bellId)
                .putExtra(EXTRA_VOLUME, volume)
            context.startForegroundService(intent)
        }
    }
}
