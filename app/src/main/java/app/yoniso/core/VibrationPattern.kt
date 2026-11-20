package app.yoniso.core

import kotlin.math.roundToInt

/** How each pulse of a vibration feels. */
enum class PulseShape {
    STEADY,

    /** Strength ramps up through each pulse. Needs amplitude control; otherwise plays as [STEADY]. */
    SWELL,
}

/**
 * A vibration, stored as the parameters the editor shows rather than as a raw waveform:
 * [pulses] pulses of [pulseMillis], separated by [gapMillis], played [repeats] times with
 * [REPEAT_GAP_MILLIS] between repeats.
 */
data class VibrationPattern(
    val pulses: Int,
    val pulseMillis: Int,
    val gapMillis: Int,
    /** From [MIN_INTENSITY] to 1. Ignored without amplitude control. */
    val intensity: Float,
    val shape: PulseShape = PulseShape.STEADY,
    val repeats: Int = 1,
) {
    fun sanitized(): VibrationPattern = copy(
        pulses = pulses.coerceIn(PULSES_RANGE),
        pulseMillis = pulseMillis.coerceIn(PULSE_MILLIS_RANGE),
        gapMillis = gapMillis.coerceIn(GAP_MILLIS_RANGE),
        intensity = if (intensity.isNaN()) 1f else intensity.coerceIn(MIN_INTENSITY, 1f),
        repeats = repeats.coerceIn(REPEATS_RANGE),
    )

    /**
     * The waveform to play: alternating off and on segments starting with off, as Android's
     * `VibrationEffect.createWaveform(timings, amplitudes, -1)` expects. Cut off at [MAX_TOTAL_MILLIS].
     *
     * Without [amplitudeControl], every on segment uses [DEFAULT_AMPLITUDE] and pulses are steady.
     */
    fun toWaveform(amplitudeControl: Boolean): Waveform {
        val p = sanitized()
        val swell = amplitudeControl && p.shape == PulseShape.SWELL
        val full = if (amplitudeControl) amplitudeOf(p.intensity) else DEFAULT_AMPLITUDE
        val timings = mutableListOf(0L)
        val amplitudes = mutableListOf(0)
        var total = 0L

        fun add(millis: Long, amplitude: Int): Boolean {
            val room = MAX_TOTAL_MILLIS - total
            if (room <= 0) return false
            val length = minOf(millis, room)
            // Consecutive off segments merge, so the list keeps alternating off/on as Android expects.
            if (amplitude == 0 && amplitudes.last() == 0) {
                timings[timings.lastIndex] += length
            } else {
                timings += length
                amplitudes += amplitude
            }
            total += length
            return length == millis
        }

        outer@ for (repeat in 0 until p.repeats) {
            if (repeat > 0 && !add(REPEAT_GAP_MILLIS, 0)) break
            for (pulse in 0 until p.pulses) {
                if (pulse > 0 && !add(p.gapMillis.toLong(), 0)) break@outer
                if (swell) {
                    // Four rising steps, each a quarter of the pulse.
                    val steps = SWELL_STEPS
                    for (step in 0 until steps) {
                        val start = p.pulseMillis * step / steps
                        val end = p.pulseMillis * (step + 1) / steps
                        val amplitude = (full * (step + 1) / steps).coerceAtLeast(1)
                        if (!add((end - start).toLong(), amplitude)) break@outer
                    }
                } else if (!add(p.pulseMillis.toLong(), full)) {
                    break@outer
                }
            }
        }
        return Waveform(timings, amplitudes)
    }

    /** How long the whole pattern would last without the [MAX_TOTAL_MILLIS] cap. */
    val fullMillis: Long
        get() {
            val p = sanitized()
            val once = p.pulses.toLong() * p.pulseMillis + (p.pulses - 1).toLong() * p.gapMillis
            return p.repeats * once + (p.repeats - 1) * REPEAT_GAP_MILLIS
        }

    /** True if [toWaveform] cuts the pattern short. */
    val isCut: Boolean get() = fullMillis > MAX_TOTAL_MILLIS

    companion object {
        val PULSES_RANGE = 1..6
        val PULSE_MILLIS_RANGE = 50..1500
        val GAP_MILLIS_RANGE = 100..1500
        val REPEATS_RANGE = 1..3
        const val MIN_INTENSITY = 0.1f
        const val REPEAT_GAP_MILLIS = 600L
        const val MAX_TOTAL_MILLIS = 6_000L
        const val SWELL_STEPS = 4

        /** Android's `VibrationEffect.DEFAULT_AMPLITUDE`. */
        const val DEFAULT_AMPLITUDE = -1

        /** Maps 0.1..1 onto Android's amplitude range 1..255. */
        fun amplitudeOf(intensity: Float): Int = (intensity * 255).roundToInt().coerceIn(1, 255)
    }
}

/** Off/on segment lengths in milliseconds, starting with off, and each segment's amplitude (0 = off). */
data class Waveform(val timings: List<Long>, val amplitudes: List<Int>) {
    val totalMillis: Long get() = timings.sum()
}

/** The vibrations offered before the user customises one. */
enum class VibrationPreset(val pattern: VibrationPattern) {
    GENTLE_TAP(VibrationPattern(pulses = 1, pulseMillis = 80, gapMillis = 200, intensity = 0.5f)),
    DOUBLE_TAP(VibrationPattern(pulses = 2, pulseMillis = 80, gapMillis = 150, intensity = 0.7f)),
    HEARTBEAT(VibrationPattern(pulses = 2, pulseMillis = 100, gapMillis = 120, intensity = 0.8f, repeats = 2)),
    THREE_SLOW(VibrationPattern(pulses = 3, pulseMillis = 400, gapMillis = 600, intensity = 0.6f)),
    SWELL(VibrationPattern(pulses = 1, pulseMillis = 1200, gapMillis = 200, intensity = 1f, shape = PulseShape.SWELL));

    companion object {
        val DEFAULT = DOUBLE_TAP

        /** The preset [pattern] matches, or null if it's custom. */
        fun of(pattern: VibrationPattern): VibrationPreset? = entries.firstOrNull { it.pattern == pattern }
    }
}
