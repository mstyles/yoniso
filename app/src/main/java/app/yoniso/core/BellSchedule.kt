package app.yoniso.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Works out when the next bell rings. Only one alarm is ever set, so after each bell (and after a
 * reboot or a settings change) the caller asks for the next ring and sets an alarm for it.
 *
 * Times are computed in local wall-clock time and then converted with [zone], so bells stay on the
 * clock across DST changes:
 * - A ring time in a skipped hour (spring forward) moves forward by the gap, as `java.time` does.
 *   It never rings twice at the same instant, because each ring is strictly after the last.
 * - A ring time in a repeated hour (fall back) uses the earlier offset, so it rings once.
 */
object BellSchedule {
    /** An exact alarm later than this (device off, deep Doze) is skipped rather than rung. */
    val LATE_TOLERANCE: Duration = Duration.ofMinutes(2)

    /** The most an inexact alarm may be late and still ring. */
    val MAX_INEXACT_TOLERANCE: Duration = Duration.ofHours(1)

    /**
     * The first ring strictly after [after], or null if bells are off or no day is active.
     * While snoozed, the first ring strictly after the snooze ends.
     */
    fun nextRing(after: Instant, settings: BellSettings, zone: ZoneId, random: Random): Instant? {
        val s = settings.sanitized()
        if (!s.enabled || s.activeHours.days.isEmpty()) return null
        val from = s.snoozedUntil?.takeIf { it > after } ?: after
        val windows = windowsAround(from, s.activeHours, zone)
        return when (s.timing) {
            BellTiming.INTERVAL -> nextAligned(from, s.intervalMinutes, windows, zone)
            BellTiming.RANDOM -> nextRandom(from, s, windows, random)
        }
    }

    /** How long "Snooze for 1 hour" lasts. */
    val SNOOZE_HOUR: Duration = Duration.ofHours(1)

    /**
     * When "Snooze until tomorrow" ends, if snoozed at [now]: midnight after today, so no more bells
     * ring today. Inside an active window, "today" is the day the window starts on, and a window that
     * crosses midnight (e.g. 22:00–02:00) is snoozed to its end.
     */
    fun snoozeUntilTomorrow(now: Instant, hours: ActiveHours, zone: ZoneId): Instant {
        val window = windowsAround(now, hours, zone).firstOrNull { now >= it.startInstant && now < it.endInstant }
        val today = window?.start?.toLocalDate() ?: now.atZone(zone).toLocalDate()
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        return maxOf(midnight, window?.endInstant ?: midnight)
    }

    /** Whether [at] falls inside an active window. */
    fun isActive(at: Instant, hours: ActiveHours, zone: ZoneId): Boolean =
        windowsAround(at, hours, zone).any { at in it.startInstant..it.endInstant }

    /** Whether an alarm due at [due] and firing at [now] is too late to ring. */
    fun isLate(due: Instant, now: Instant, tolerance: Duration = LATE_TOLERANCE): Boolean =
        Duration.between(due, now) > tolerance

    /**
     * How late an alarm set at [setAt] for [due] may fire and still ring.
     *
     * An exact alarm should fire on time, so [LATE_TOLERANCE]. Android may deliver an inexact alarm
     * up to three quarters of its lead time late, as normal batching, so that much is allowed on top
     * (up to [MAX_INEXACT_TOLERANCE]). Otherwise most inexact bells would be skipped as late.
     */
    fun lateTolerance(setAt: Instant, due: Instant, exact: Boolean): Duration {
        if (exact) return LATE_TOLERANCE
        val lead = Duration.between(setAt, due).coerceAtLeast(Duration.ZERO)
        return minOf(LATE_TOLERANCE + lead.multipliedBy(3).dividedBy(4), MAX_INEXACT_TOLERANCE)
    }

    private fun nextAligned(from: Instant, minutes: Int, windows: List<Window>, zone: ZoneId): Instant? {
        for (window in windows) {
            if (window.endInstant <= from) continue
            var slot = firstAlignedSlot(window.start, minutes)
            var best: Instant? = null
            while (slot <= window.end) {
                val instant = slot.atZone(zone).toInstant()
                if (instant > from && (best == null || instant < best)) best = instant
                slot = slot.plusMinutes(minutes.toLong())
            }
            if (best != null) return best
        }
        return null
    }

    /** The first time at or after [start] whose minutes since midnight are a multiple of [minutes]. */
    private fun firstAlignedSlot(start: LocalDateTime, minutes: Int): LocalDateTime {
        val minuteOfDay = start.hour * 60 + start.minute
        val aligned = (minuteOfDay + minutes - 1) / minutes * minutes
        return start.toLocalDate().atStartOfDay().plusMinutes(aligned.toLong())
    }

    /**
     * Inside an active span: a random gap after [from], if that's still inside it. Otherwise a random
     * gap after the start of the next span, so the first bell of the day isn't always at its start.
     * Windows that touch (e.g. all day, every day) form one span, so the gap doesn't restart at
     * midnight. A span shorter than the minimum gap never rings.
     */
    private fun nextRandom(from: Instant, s: BellSettings, windows: List<Window>, random: Random): Instant? {
        val spans = spansOf(windows)
        spans.firstOrNull { from in it }?.let { current ->
            val candidate = from + randomGap(s, random)
            if (candidate <= current.endInclusive) return candidate
        }
        for (span in spans) {
            if (span.start <= from) continue
            val candidate = span.start + randomGap(s, random)
            if (candidate <= span.endInclusive) return candidate
        }
        return null
    }

    /** [windows] (in order) as instant ranges, with touching or overlapping windows merged. */
    private fun spansOf(windows: List<Window>): List<ClosedRange<Instant>> {
        val spans = mutableListOf<ClosedRange<Instant>>()
        for (w in windows) {
            val last = spans.lastOrNull()
            spans += if (last != null && w.startInstant <= last.endInclusive) {
                spans.removeAt(spans.lastIndex)
                last.start..maxOf(last.endInclusive, w.endInstant)
            } else {
                w.startInstant..w.endInstant
            }
        }
        return spans
    }

    private fun randomGap(s: BellSettings, random: Random): Duration {
        val min = s.randomMinMinutes * 60_000L
        val max = s.randomMaxMinutes * 60_000L
        return Duration.ofMillis(random.nextLong(min, max + 1))
    }

    /**
     * Active windows that could contain [at] or follow it within a week, in order. Starts the day
     * before, because a window crossing midnight belongs to the day it starts on.
     */
    private fun windowsAround(at: Instant, hours: ActiveHours, zone: ZoneId): List<Window> {
        val firstDay = at.atZone(zone).toLocalDate().minusDays(1)
        return (0L..8L).map { firstDay.plusDays(it) }
            .filter { it.dayOfWeek in hours.days }
            .map { Window.of(it, hours, zone) }
    }

    private class Window(val start: LocalDateTime, val end: LocalDateTime, zone: ZoneId) {
        val startInstant: Instant = start.atZone(zone).toInstant()
        val endInstant: Instant = end.atZone(zone).toInstant()

        companion object {
            fun of(day: LocalDate, hours: ActiveHours, zone: ZoneId): Window {
                val endDay = if (hours.end > hours.start) day else day.plusDays(1)
                return Window(day.atTime(hours.start), endDay.atTime(hours.end), zone)
            }
        }
    }
}
