package app.yoniso.bells

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import app.yoniso.core.BellSettings
import app.yoniso.core.BellTiming
import app.yoniso.data.BellRepository
import app.yoniso.ui.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The "Bells" Quick Settings tile. A tap turns bells on or off; the subtitle (Android 10+) says when
 * the next bell is due, or that bells are snoozed. A long press opens the bells screen.
 *
 * Turning bells on here doesn't ask for notifications or exact alarms, which a tile can't do; the
 * bells screen shows what's missing.
 */
class BellTileService : TileService() {
    private var listening: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        listening = scope
        val repository = BellRepository(this)
        scope.launch {
            combine(repository.settings, repository.nextRingAt, ::Pair).collect { (settings, next) -> show(settings, next) }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext
        BellAlarms.scope.launch {
            val enabled = BellRepository(app).settings.first().enabled
            BellAlarms.setEnabled(app, !enabled)
        }
    }

    private fun show(settings: BellSettings, nextRing: Instant?) {
        val tile = qsTile ?: return
        tile.state = if (settings.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle(settings, nextRing, Instant.now(), DateFormat.is24HourFormat(this))
        }
        tile.updateTile()
    }

    companion object {
        /** Asks Android to update the tile, if the user has added it. */
        fun refresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, BellTileService::class.java))
            } catch (_: Exception) {
                // Thrown by some versions when the tile isn't added; nothing to update then.
            }
        }

        /** Short, for under the tile's label: "Off", "Snoozed", "Next 10:40", "Around 10:40". */
        internal fun subtitle(settings: BellSettings, nextRing: Instant?, now: Instant, is24Hour: Boolean): String {
            if (!settings.enabled) return "Off"
            val snoozed = settings.snoozedUntil
            if (snoozed != null && snoozed > now) return "Snoozed"
            nextRing ?: return "On"
            val zone = ZoneId.systemDefault()
            val at = nextRing.atZone(zone)
            if (at.toLocalDate() != LocalDate.now(zone)) return "On"
            val time = formatTime(at.toLocalTime(), is24Hour)
            return if (settings.timing == BellTiming.RANDOM) "Around $time" else "Next $time"
        }
    }
}
