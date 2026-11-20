package app.yoniso.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The phone's sound mode, from `AudioManager.ringerMode`. */
enum class RingerMode { NORMAL, VIBRATE, SILENT }

/** What the phone is doing when a bell is due. */
data class PhoneState(
    val ringerMode: RingerMode = RingerMode.NORMAL,
    /** Any Do Not Disturb filter (`currentInterruptionFilter != INTERRUPTION_FILTER_ALL`). */
    val doNotDisturb: Boolean = false,
    /** `AudioManager.mode != MODE_NORMAL`: ringing, in a call, or in a VoIP call. */
    val inCall: Boolean = false,
    val mediaPlaying: Boolean = false,
    val pauseShowing: Boolean = false,
    /**
     * Whether the bell service can start: an exact alarm fired, or the accessibility service is on.
     * Otherwise Android refuses a background start, so the bell can only vibrate.
     */
    val canPlaySound: Boolean = true,
)

/** Why a bell didn't ring. */
enum class SkipReason { DISABLED, LATE, SNOOZED, OUTSIDE_HOURS, DO_NOT_DISTURB, SILENT, CALL, PAUSE_SHOWING, MEDIA }

sealed interface RingDecision {
    data class Ring(val sound: Boolean, val vibrate: Boolean) : RingDecision
    data class Skip(val reason: SkipReason) : RingDecision
}

/**
 * Decides what a bell due at `due` does when its alarm fires at `now`.
 *
 * The bell plays on the alarm stream, which ignores the ringer and Do Not Disturb, so this checks
 * them itself. Vibrate mode still rings: many people keep their phone on vibrate only to silence
 * calls. When the bell can't play (media playing with [DuringMedia.VIBRATE], or no way to start
 * the bell service), it vibrates instead, so the reminder isn't lost.
 *
 * [lateTolerance] is how late the alarm may fire and still ring (see [BellSchedule.lateTolerance]).
 */
object RingDecider {
    fun decide(
        due: Instant,
        now: Instant,
        settings: BellSettings,
        zone: ZoneId,
        phone: PhoneState,
        lateTolerance: Duration = BellSchedule.LATE_TOLERANCE,
    ): RingDecision {
        val s = settings.sanitized()
        val skip = when {
            !s.enabled -> SkipReason.DISABLED
            BellSchedule.isLate(due, now, lateTolerance) -> SkipReason.LATE
            s.snoozedUntil != null && now < s.snoozedUntil -> SkipReason.SNOOZED
            !BellSchedule.isActive(due, s.activeHours, zone) -> SkipReason.OUTSIDE_HOURS
            phone.doNotDisturb -> SkipReason.DO_NOT_DISTURB
            phone.ringerMode == RingerMode.SILENT -> SkipReason.SILENT
            phone.inCall -> SkipReason.CALL
            phone.pauseShowing -> SkipReason.PAUSE_SHOWING
            phone.mediaPlaying && s.duringMedia == DuringMedia.SKIP -> SkipReason.MEDIA
            else -> null
        }
        if (skip != null) return RingDecision.Skip(skip)

        val soundBlocked = !phone.canPlaySound || (phone.mediaPlaying && s.duringMedia == DuringMedia.VIBRATE)
        return if (s.output.sound && soundBlocked) {
            RingDecision.Ring(sound = false, vibrate = true)
        } else {
            RingDecision.Ring(sound = s.output.sound, vibrate = s.output.vibrate)
        }
    }
}
