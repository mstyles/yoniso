package app.yoniso.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.yoniso.core.ActiveHours
import app.yoniso.core.BellSettings
import app.yoniso.core.VibrationPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

// Kept apart from the pause settings, so bell changes don't make the accessibility service re-emit.
private val Context.bellStore by preferencesDataStore(name = "bells")

/**
 * The mindfulness bell settings and the time of the next ring, stored on the device with DataStore.
 * Missing or unreadable values fall back to the defaults in [BellSettings].
 *
 * After changing the settings, call `BellAlarms.reschedule()` so the alarm follows them.
 */
class BellRepository(context: Context) {
    private val store = context.applicationContext.bellStore

    /** Always sanitized, so stale stored values can't reach the schedule. */
    val settings: Flow<BellSettings> = store.safeData()
        .map { it.toBellSettings() }
        .distinctUntilChanged()

    suspend fun update(transform: (BellSettings) -> BellSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toBellSettings()).sanitized()) }
    }

    /** When the alarm that is set now is due, or null if none is set. */
    suspend fun nextRingAt(): Instant? = store.safeData().first()[NEXT_RING_AT]?.let(Instant::ofEpochMilli)

    suspend fun setNextRingAt(at: Instant?) {
        store.edit { if (at == null) it.remove(NEXT_RING_AT) else it[NEXT_RING_AT] = at.toEpochMilli() }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val TIMING = stringPreferencesKey("timing")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val RANDOM_MIN_MINUTES = intPreferencesKey("random_min_minutes")
        val RANDOM_MAX_MINUTES = intPreferencesKey("random_max_minutes")
        /** Minutes since midnight. */
        val ACTIVE_START = intPreferencesKey("active_start")
        val ACTIVE_END = intPreferencesKey("active_end")
        val ACTIVE_DAYS = stringSetPreferencesKey("active_days")
        val OUTPUT = stringPreferencesKey("output")
        val BELL_ID = stringPreferencesKey("bell_id")
        val VOLUME = floatPreferencesKey("volume")
        val VIBRATION_PULSES = intPreferencesKey("vibration_pulses")
        val VIBRATION_PULSE_MILLIS = intPreferencesKey("vibration_pulse_millis")
        val VIBRATION_GAP_MILLIS = intPreferencesKey("vibration_gap_millis")
        val VIBRATION_INTENSITY = floatPreferencesKey("vibration_intensity")
        val VIBRATION_SHAPE = stringPreferencesKey("vibration_shape")
        val VIBRATION_REPEATS = intPreferencesKey("vibration_repeats")
        val DURING_MEDIA = stringPreferencesKey("during_media")
        val SNOOZED_UNTIL = longPreferencesKey("snoozed_until")
        val NEXT_RING_AT = longPreferencesKey("next_ring_at")

        fun Preferences.toBellSettings(): BellSettings {
            val d = BellSettings()
            val dv = d.vibration
            return BellSettings(
                enabled = this[ENABLED] ?: d.enabled,
                timing = enumOf(this[TIMING], d.timing),
                intervalMinutes = this[INTERVAL_MINUTES] ?: d.intervalMinutes,
                randomMinMinutes = this[RANDOM_MIN_MINUTES] ?: d.randomMinMinutes,
                randomMaxMinutes = this[RANDOM_MAX_MINUTES] ?: d.randomMaxMinutes,
                activeHours = ActiveHours(
                    start = timeOf(this[ACTIVE_START]) ?: d.activeHours.start,
                    end = timeOf(this[ACTIVE_END]) ?: d.activeHours.end,
                    days = this[ACTIVE_DAYS]?.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }?.toSet()
                        ?: d.activeHours.days,
                ),
                output = enumOf(this[OUTPUT], d.output),
                bellId = this[BELL_ID] ?: d.bellId,
                volume = this[VOLUME] ?: d.volume,
                vibration = VibrationPattern(
                    pulses = this[VIBRATION_PULSES] ?: dv.pulses,
                    pulseMillis = this[VIBRATION_PULSE_MILLIS] ?: dv.pulseMillis,
                    gapMillis = this[VIBRATION_GAP_MILLIS] ?: dv.gapMillis,
                    intensity = this[VIBRATION_INTENSITY] ?: dv.intensity,
                    shape = enumOf(this[VIBRATION_SHAPE], dv.shape),
                    repeats = this[VIBRATION_REPEATS] ?: dv.repeats,
                ),
                duringMedia = enumOf(this[DURING_MEDIA], d.duringMedia),
                snoozedUntil = this[SNOOZED_UNTIL]?.let(Instant::ofEpochMilli),
            ).sanitized()
        }

        fun MutablePreferences.write(s: BellSettings) {
            this[ENABLED] = s.enabled
            this[TIMING] = s.timing.name
            this[INTERVAL_MINUTES] = s.intervalMinutes
            this[RANDOM_MIN_MINUTES] = s.randomMinMinutes
            this[RANDOM_MAX_MINUTES] = s.randomMaxMinutes
            this[ACTIVE_START] = s.activeHours.start.toSecondOfDay() / 60
            this[ACTIVE_END] = s.activeHours.end.toSecondOfDay() / 60
            this[ACTIVE_DAYS] = s.activeHours.days.map { it.name }.toSet()
            this[OUTPUT] = s.output.name
            this[BELL_ID] = s.bellId
            this[VOLUME] = s.volume
            this[VIBRATION_PULSES] = s.vibration.pulses
            this[VIBRATION_PULSE_MILLIS] = s.vibration.pulseMillis
            this[VIBRATION_GAP_MILLIS] = s.vibration.gapMillis
            this[VIBRATION_INTENSITY] = s.vibration.intensity
            this[VIBRATION_SHAPE] = s.vibration.shape.name
            this[VIBRATION_REPEATS] = s.vibration.repeats
            this[DURING_MEDIA] = s.duringMedia.name
            val snoozedUntil = s.snoozedUntil
            if (snoozedUntil == null) remove(SNOOZED_UNTIL) else this[SNOOZED_UNTIL] = snoozedUntil.toEpochMilli()
        }

        inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default

        fun timeOf(minutes: Int?): LocalTime? =
            minutes?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime.of(it / 60, it % 60) }
    }
}
