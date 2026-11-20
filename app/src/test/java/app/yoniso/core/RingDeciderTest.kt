package app.yoniso.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class RingDeciderTest {

    private val zone = ZoneId.of("Europe/London")
    private val due: Instant = LocalDateTime.parse("2026-11-11T10:00").atZone(zone).toInstant()
    private val on = BellSettings(enabled = true, output = BellOutput.BOTH)

    private fun decide(
        settings: BellSettings = on,
        phone: PhoneState = PhoneState(),
        now: Instant = due.plusMillis(30),
        at: Instant = due,
    ) = RingDecider.decide(at, now, settings, zone, phone)

    private fun ring(sound: Boolean, vibrate: Boolean) = RingDecision.Ring(sound, vibrate)
    private fun skip(reason: SkipReason) = RingDecision.Skip(reason)

    @Test
    fun `rings as configured`() {
        assertEquals(ring(sound = true, vibrate = true), decide())
        assertEquals(ring(sound = true, vibrate = false), decide(on.copy(output = BellOutput.BELL)))
        assertEquals(ring(sound = false, vibrate = true), decide(on.copy(output = BellOutput.VIBRATE)))
    }

    @Test
    fun `vibrate mode still rings`() {
        assertEquals(ring(sound = true, vibrate = true), decide(phone = PhoneState(ringerMode = RingerMode.VIBRATE)))
    }

    @Test
    fun `silent skips`() {
        assertEquals(skip(SkipReason.SILENT), decide(phone = PhoneState(ringerMode = RingerMode.SILENT)))
    }

    @Test
    fun `do not disturb skips, even for vibration`() {
        assertEquals(skip(SkipReason.DO_NOT_DISTURB), decide(on.copy(output = BellOutput.VIBRATE), PhoneState(doNotDisturb = true)))
    }

    @Test
    fun `a call skips`() {
        assertEquals(skip(SkipReason.CALL), decide(phone = PhoneState(inCall = true)))
    }

    @Test
    fun `a pause showing skips`() {
        assertEquals(skip(SkipReason.PAUSE_SHOWING), decide(phone = PhoneState(pauseShowing = true)))
    }

    @Test
    fun `media playing follows the setting`() {
        val media = PhoneState(mediaPlaying = true)
        assertEquals(ring(sound = false, vibrate = true), decide(on.copy(output = BellOutput.BELL, duringMedia = DuringMedia.VIBRATE), media))
        assertEquals(ring(sound = true, vibrate = true), decide(on.copy(duringMedia = DuringMedia.RING), media))
        assertEquals(skip(SkipReason.MEDIA), decide(on.copy(duringMedia = DuringMedia.SKIP), media))
    }

    @Test
    fun `media playing doesn't change vibration-only bells`() {
        assertEquals(ring(sound = false, vibrate = true), decide(on.copy(output = BellOutput.VIBRATE), PhoneState(mediaPlaying = true)))
    }

    @Test
    fun `no way to play sound falls back to vibration`() {
        assertEquals(ring(sound = false, vibrate = true), decide(on.copy(output = BellOutput.BELL), PhoneState(canPlaySound = false)))
        assertEquals(ring(sound = false, vibrate = true), decide(phone = PhoneState(canPlaySound = false)))
    }

    @Test
    fun `disabled skips`() {
        assertEquals(skip(SkipReason.DISABLED), decide(on.copy(enabled = false)))
    }

    @Test
    fun `late alarms skip`() {
        assertEquals(ring(sound = true, vibrate = true), decide(now = due.plusSeconds(120)))
        assertEquals(skip(SkipReason.LATE), decide(now = due.plusSeconds(121)))
    }

    @Test
    fun `snoozed skips until the snooze ends`() {
        assertEquals(skip(SkipReason.SNOOZED), decide(on.copy(snoozedUntil = due.plusSeconds(60))))
        assertEquals(ring(sound = true, vibrate = true), decide(on.copy(snoozedUntil = due)))
    }

    @Test
    fun `outside active hours skips`() {
        val night = LocalDateTime.parse("2026-11-11T23:00").atZone(zone).toInstant()
        assertEquals(skip(SkipReason.OUTSIDE_HOURS), decide(at = night, now = night))
    }

    @Test
    fun `the first matching reason wins`() {
        assertEquals(
            skip(SkipReason.DO_NOT_DISTURB),
            decide(phone = PhoneState(doNotDisturb = true, ringerMode = RingerMode.SILENT, inCall = true)),
        )
    }
}
