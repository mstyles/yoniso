package app.yoniso.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** How bell times are chosen. */
enum class BellTiming {
    /** Every [BellSettings.intervalMinutes], aligned to the clock (e.g. :00, :15, :30, :45). */
    INTERVAL,

    /** Each gap picked evenly between [BellSettings.randomMinMinutes] and [BellSettings.randomMaxMinutes]. */
    RANDOM,
}

/** What a bell does. */
enum class BellOutput(val sound: Boolean, val vibrate: Boolean) {
    BELL(sound = true, vibrate = false),
    VIBRATE(sound = false, vibrate = true),
    BOTH(sound = true, vibrate = true),
}

/** What a bell does while music or video is playing. */
enum class DuringMedia {
    /** Vibrate instead of playing the bell over it. */
    VIBRATE,
    RING,
    SKIP,
}

/**
 * When bells may ring: from [start] to [end] (inclusive) on each of [days].
 *
 * A window whose [end] is before its [start] (e.g. 22:00–02:00) crosses midnight and belongs to the
 * day it starts on. A window whose [end] equals its [start] lasts the full 24 hours.
 */
data class ActiveHours(
    val start: LocalTime = LocalTime.of(8, 0),
    val end: LocalTime = LocalTime.of(21, 0),
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
)

/** The user's mindfulness bell settings, as far as the bell logic needs them. */
data class BellSettings(
    val enabled: Boolean = false,
    val timing: BellTiming = BellTiming.RANDOM,
    val intervalMinutes: Int = 15,
    val randomMinMinutes: Int = 10,
    val randomMaxMinutes: Int = 30,
    val activeHours: ActiveHours = ActiveHours(),
    val output: BellOutput = BellOutput.BELL,
    val bellId: String = DEFAULT_BELL_ID,
    /** Relative to the alarm volume, from [MIN_VOLUME] to 1. */
    val volume: Float = 0.5f,
    val vibration: VibrationPattern = VibrationPreset.DEFAULT.pattern,
    val duringMedia: DuringMedia = DuringMedia.VIBRATE,
    /** No bell rings before this time. Null when not snoozed. */
    val snoozedUntil: Instant? = null,
) {
    /**
     * A copy with every value in range, so stale or hand-edited stored values can't break the
     * schedule: an interval that isn't offered becomes the default, random bounds are clamped and
     * ordered, times are cut to whole minutes, and the volume is clamped.
     */
    fun sanitized(): BellSettings {
        val min = randomMinMinutes.coerceIn(RANDOM_MIN_MINUTES, RANDOM_MAX_MINUTES)
        return copy(
            intervalMinutes = intervalMinutes.takeIf { it in INTERVAL_OPTIONS } ?: DEFAULT_INTERVAL_MINUTES,
            randomMinMinutes = min,
            randomMaxMinutes = randomMaxMinutes.coerceIn(min, RANDOM_MAX_MINUTES),
            activeHours = activeHours.copy(
                start = activeHours.start.withSecond(0).withNano(0),
                end = activeHours.end.withSecond(0).withNano(0),
            ),
            volume = if (volume.isNaN()) DEFAULT_VOLUME else volume.coerceIn(MIN_VOLUME, 1f),
            vibration = vibration.sanitized(),
        )
    }

    companion object {
        /** Every option divides a day evenly, so aligning to midnight also aligns to the hour where it can. */
        val INTERVAL_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
        const val DEFAULT_INTERVAL_MINUTES = 15

        /** Shorter gaps aren't reliable: in Doze, Android delays alarms to about one per 9 minutes. */
        const val RANDOM_MIN_MINUTES = 5
        const val RANDOM_MAX_MINUTES = 240

        const val MIN_VOLUME = 0.1f
        const val DEFAULT_VOLUME = 0.5f

        const val DEFAULT_BELL_ID = "plum_village"
    }
}
