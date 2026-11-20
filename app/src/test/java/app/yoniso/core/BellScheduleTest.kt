package app.yoniso.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

class BellScheduleTest {

    // New York: DST starts 2026-03-08 (02:00 -> 03:00) and ends 2026-11-01 (02:00 -> 01:00).
    private val zone = ZoneId.of("America/New_York")

    /** 2026-11-11 is a Wednesday. */
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun local(instant: Instant): LocalDateTime = instant.atZone(zone).toLocalDateTime()

    private fun hours(start: String, end: String, days: Set<DayOfWeek> = DayOfWeek.entries.toSet()) =
        ActiveHours(LocalTime.parse(start), LocalTime.parse(end), days)

    private fun interval(minutes: Int, hours: ActiveHours = hours("08:00", "21:00")) =
        BellSettings(enabled = true, timing = BellTiming.INTERVAL, intervalMinutes = minutes, activeHours = hours)

    private fun random(min: Int, max: Int, hours: ActiveHours = hours("08:00", "21:00")) =
        BellSettings(enabled = true, timing = BellTiming.RANDOM, randomMinMinutes = min, randomMaxMinutes = max, activeHours = hours)

    private fun next(after: Instant, settings: BellSettings, seed: Int = 1): Instant? =
        BellSchedule.nextRing(after, settings, zone, Random(seed))

    /** Rings from [start] onwards, each strictly after the last. */
    private fun rings(start: Instant, settings: BellSettings, count: Int, seed: Int = 1): List<Instant> {
        val rng = Random(seed)
        val out = mutableListOf<Instant>()
        var t = start
        repeat(count) {
            val n = BellSchedule.nextRing(t, settings, zone, rng) ?: return out
            out += n
            t = n
        }
        return out
    }

    // --- On / off ---

    @Test
    fun `disabled has no next ring`() {
        assertNull(next(at("2026-11-11T09:00"), interval(15).copy(enabled = false)))
    }

    @Test
    fun `no active days has no next ring`() {
        assertNull(next(at("2026-11-11T09:00"), interval(15, hours("08:00", "21:00", emptySet()))))
        assertNull(next(at("2026-11-11T09:00"), random(10, 30, hours("08:00", "21:00", emptySet()))))
    }

    // --- Interval: clock alignment ---

    @Test
    fun `15 minutes rings on the quarter hour`() {
        assertEquals(
            listOf("09:15", "09:30", "09:45", "10:00").map { at("2026-11-11T$it") },
            rings(at("2026-11-11T09:07"), interval(15), 4),
        )
    }

    @Test
    fun `a ring time is strictly after the given time`() {
        assertEquals(at("2026-11-11T09:15"), next(at("2026-11-11T09:00"), interval(15)))
    }

    @Test
    fun `45 minutes aligns to midnight`() {
        // 45-minute slots from midnight: ..., 08:15, 09:00, 09:45, 10:30, ...
        assertEquals(
            listOf("09:45", "10:30", "11:15").map { at("2026-11-11T$it") },
            rings(at("2026-11-11T09:00"), interval(45), 3),
        )
    }

    @Test
    fun `90 minutes aligns to midnight`() {
        // 90-minute slots: 07:30, 09:00, 10:30, ...
        assertEquals(
            listOf("09:00", "10:30", "12:00").map { at("2026-11-11T$it") },
            rings(at("2026-11-11T08:00"), interval(90), 3),
        )
    }

    @Test
    fun `first ring of the day is the first aligned slot at or after the window start`() {
        assertEquals(at("2026-11-12T08:00"), next(at("2026-11-11T21:00"), interval(15)))
        assertEquals(at("2026-11-12T08:15"), next(at("2026-11-11T22:00"), interval(15, hours("08:10", "21:00"))))
    }

    @Test
    fun `window end is inclusive`() {
        assertEquals(at("2026-11-11T21:00"), next(at("2026-11-11T20:45"), interval(15)))
    }

    @Test
    fun `ring before the window waits for its start`() {
        assertEquals(at("2026-11-11T08:00"), next(at("2026-11-11T03:00"), interval(60)))
    }

    // --- Days ---

    @Test
    fun `inactive days are skipped`() {
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        // Friday 2026-11-13 evening -> Monday 2026-11-16.
        assertEquals(at("2026-11-16T08:00"), next(at("2026-11-13T21:30"), interval(30, hours("08:00", "21:00", weekdays))))
    }

    @Test
    fun `a single active day a week later is found`() {
        val wednesdays = setOf(DayOfWeek.WEDNESDAY)
        assertEquals(at("2026-11-18T08:00"), next(at("2026-11-11T21:30"), interval(30, hours("08:00", "21:00", wednesdays))))
    }

    // --- Windows crossing midnight ---

    @Test
    fun `a window crossing midnight rings past midnight`() {
        val night = hours("22:00", "02:00")
        assertEquals(
            listOf("2026-11-11T23:00", "2026-11-12T00:00", "2026-11-12T01:00", "2026-11-12T02:00", "2026-11-12T22:00").map { at(it) },
            rings(at("2026-11-11T22:30"), interval(60, night), 5),
        )
    }

    @Test
    fun `a window crossing midnight belongs to the day it starts on`() {
        // Only Wednesday: Wednesday 22:00 to Thursday 02:00 rings, Thursday night does not.
        val night = hours("22:00", "02:00", setOf(DayOfWeek.WEDNESDAY))
        assertEquals(at("2026-11-12T01:00"), next(at("2026-11-12T00:30"), interval(60, night)))
        assertEquals(at("2026-11-18T22:00"), next(at("2026-11-12T02:00"), interval(60, night)))
    }

    @Test
    fun `start equal to end is active all day`() {
        val allDay = hours("00:00", "00:00")
        assertEquals(
            listOf("2026-11-11T23:00", "2026-11-12T00:00", "2026-11-12T01:00").map { at(it) },
            rings(at("2026-11-11T22:30"), interval(60, allDay), 3),
        )
    }

    // --- Random ---

    @Test
    fun `random gaps stay within min and max`() {
        val allDay = hours("00:00", "00:00")
        val list = rings(at("2026-11-11T00:00"), random(10, 30, allDay), 200)
        assertEquals(200, list.size)
        (listOf(at("2026-11-11T00:00")) + list).zipWithNext().forEach { (a, b) ->
            val gap = Duration.between(a, b)
            assertTrue("gap $gap", gap >= Duration.ofMinutes(10) && gap <= Duration.ofMinutes(30))
        }
    }

    @Test
    fun `random gaps are spread across the range`() {
        val allDay = hours("00:00", "00:00")
        val gaps = (listOf(at("2026-11-11T00:00")) + rings(at("2026-11-11T00:00"), random(10, 30, allDay), 300))
            .zipWithNext { a, b -> Duration.between(a, b).toMinutes() }
        assertTrue(gaps.any { it < 15 })
        assertTrue(gaps.any { it >= 25 })
    }

    @Test
    fun `random all day keeps its gap across midnight`() {
        val allDay = hours("00:00", "00:00")
        // Exactly on the boundary between two days' windows, and just before midnight.
        assertEquals(at("2026-11-11T00:20"), next(at("2026-11-11T00:00"), random(20, 20, allDay)))
        assertEquals(at("2026-11-12T00:10"), next(at("2026-11-11T23:50"), random(20, 20, allDay)))
    }

    @Test
    fun `equal min and max gives a fixed gap`() {
        assertEquals(at("2026-11-11T09:20"), next(at("2026-11-11T09:00"), random(20, 20)))
    }

    @Test
    fun `random stays inside active hours`() {
        val list = rings(at("2026-11-11T08:00"), random(10, 30), 300)
        assertTrue(list.all { BellSchedule.isActive(it, hours("08:00", "21:00"), zone) })
        assertTrue(list.map { local(it).toLocalDate() }.distinct().size > 3)
    }

    @Test
    fun `random starts a fresh gap at the window start`() {
        // From the evening, the next ring is 10-30 minutes after 08:00 the next day, not at 08:00.
        val n = next(at("2026-11-11T20:55"), random(10, 30))!!
        val gap = Duration.between(at("2026-11-12T08:00"), n)
        assertTrue("$n", gap >= Duration.ofMinutes(10) && gap <= Duration.ofMinutes(30))
    }

    @Test
    fun `a window shorter than the minimum gap never rings`() {
        val tiny = hours("08:00", "08:05", setOf(DayOfWeek.WEDNESDAY))
        assertNull(next(at("2026-11-11T07:00"), random(10, 30, tiny)))
    }

    @Test
    fun `the same seed gives the same rings`() {
        assertEquals(rings(at("2026-11-11T08:00"), random(10, 30), 20, seed = 7), rings(at("2026-11-11T08:00"), random(10, 30), 20, seed = 7))
    }

    // --- Snooze ---

    @Test
    fun `snooze delays interval rings until it ends`() {
        val s = interval(15).copy(snoozedUntil = at("2026-11-11T10:05"))
        assertEquals(at("2026-11-11T10:15"), next(at("2026-11-11T09:00"), s))
    }

    @Test
    fun `snooze delays random rings by a gap after it ends`() {
        val s = random(10, 30).copy(snoozedUntil = at("2026-11-11T10:00"))
        val gap = Duration.between(at("2026-11-11T10:00"), next(at("2026-11-11T09:00"), s)!!)
        assertTrue(gap >= Duration.ofMinutes(10) && gap <= Duration.ofMinutes(30))
    }

    @Test
    fun `an expired snooze has no effect`() {
        val s = interval(15).copy(snoozedUntil = at("2026-11-11T08:00"))
        assertEquals(at("2026-11-11T09:15"), next(at("2026-11-11T09:00"), s))
    }

    @Test
    fun `snooze until tomorrow ends at midnight`() {
        val until = BellSchedule.snoozeUntilTomorrow(at("2026-11-11T10:00"), hours("08:00", "21:00"), zone)
        assertEquals(at("2026-11-12T00:00"), until)
    }

    @Test
    fun `snooze until tomorrow before the window skips the whole of today`() {
        val until = BellSchedule.snoozeUntilTomorrow(at("2026-11-11T06:00"), hours("08:00", "21:00"), zone)
        assertEquals(at("2026-11-12T00:00"), until)
    }

    @Test
    fun `snooze until tomorrow inside a window crossing midnight lasts to its end`() {
        val late = hours("22:00", "02:00")
        assertEquals(at("2026-11-12T02:00"), BellSchedule.snoozeUntilTomorrow(at("2026-11-11T23:00"), late, zone))
        // After midnight, still in last night's window.
        assertEquals(at("2026-11-12T02:00"), BellSchedule.snoozeUntilTomorrow(at("2026-11-12T01:00"), late, zone))
    }

    @Test
    fun `snooze until tomorrow then rings at the next window`() {
        val s = interval(15)
        val snoozed = s.copy(snoozedUntil = BellSchedule.snoozeUntilTomorrow(at("2026-11-11T10:00"), s.activeHours, zone))
        assertEquals(at("2026-11-12T08:00"), next(at("2026-11-11T10:00"), snoozed))
    }

    // --- DST ---

    @Test
    fun `spring forward rings once per real slot and none in the skipped hour`() {
        val allDay = hours("00:00", "00:00")
        val list = rings(at("2026-03-08T01:00"), interval(30, allDay), 4)
        // 01:30 EST, then 02:00 doesn't exist and moves to 03:00 EDT, 02:30 to 03:30, then 04:00.
        assertEquals(listOf("01:30", "03:00", "03:30", "04:00"), list.map { local(it).toLocalTime().toString() })
        assertEquals(list, list.distinct())
        assertEquals(list, list.sorted())
    }

    @Test
    fun `fall back rings once in the repeated hour`() {
        val allDay = hours("00:00", "00:00")
        val list = rings(at("2026-11-01T00:30"), interval(30, allDay), 4)
        // 01:00 and 01:30 ring on the first pass (EDT); the repeated hour is skipped.
        assertEquals(listOf("01:00", "01:30", "02:00", "02:30"), list.map { local(it).toLocalTime().toString() })
        assertEquals(Duration.ofMinutes(90), Duration.between(list[1], list[2]))
    }

    @Test
    fun `hourly rings stay on the hour across DST`() {
        val list = rings(at("2026-03-07T20:30"), interval(60), 3)
        assertEquals(listOf("2026-03-07T21:00", "2026-03-08T08:00", "2026-03-08T09:00").map { LocalDateTime.parse(it) }, list.map { local(it) })
    }

    // --- Active and late ---

    @Test
    fun `isActive covers the window inclusively`() {
        val h = hours("08:00", "21:00")
        assertTrue(BellSchedule.isActive(at("2026-11-11T08:00"), h, zone))
        assertTrue(BellSchedule.isActive(at("2026-11-11T21:00"), h, zone))
        assertFalse(BellSchedule.isActive(at("2026-11-11T21:01"), h, zone))
        assertFalse(BellSchedule.isActive(at("2026-11-11T07:59"), h, zone))
    }

    @Test
    fun `isLate allows up to two minutes`() {
        val due = at("2026-11-11T09:00")
        assertFalse(BellSchedule.isLate(due, due.plusSeconds(120)))
        assertTrue(BellSchedule.isLate(due, due.plusSeconds(121)))
        assertFalse(BellSchedule.isLate(due, due.minusSeconds(5)))
    }

    @Test
    fun `exact alarms get two minutes`() {
        val due = at("2026-11-11T09:00")
        assertEquals(Duration.ofMinutes(2), BellSchedule.lateTolerance(due.minusSeconds(1800), due, exact = true))
    }

    @Test
    fun `inexact alarms may be late by three quarters of their lead time`() {
        val due = at("2026-11-11T09:00")
        // Set 20 minutes ahead: Android may deliver up to 15 minutes late.
        assertEquals(Duration.ofMinutes(17), BellSchedule.lateTolerance(due.minusSeconds(1200), due, exact = false))
        // A long lead is capped at an hour.
        assertEquals(Duration.ofHours(1), BellSchedule.lateTolerance(due.minusSeconds(4 * 3600), due, exact = false))
        // Set after it was due (e.g. a clock change): no extra allowance.
        assertEquals(Duration.ofMinutes(2), BellSchedule.lateTolerance(due.plusSeconds(60), due, exact = false))
    }
}
