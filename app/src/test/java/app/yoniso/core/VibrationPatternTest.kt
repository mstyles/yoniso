package app.yoniso.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationPatternTest {

    private fun pattern(
        pulses: Int = 2,
        pulse: Int = 100,
        gap: Int = 200,
        intensity: Float = 1f,
        shape: PulseShape = PulseShape.STEADY,
        repeats: Int = 1,
    ) = VibrationPattern(pulses, pulse, gap, intensity, shape, repeats)

    @Test
    fun `steady pulses alternate off and on`() {
        assertEquals(
            Waveform(listOf(0L, 100, 200, 100), listOf(0, 255, 0, 255)),
            pattern().toWaveform(amplitudeControl = true),
        )
    }

    @Test
    fun `intensity scales the amplitude`() {
        assertEquals(listOf(0, 128, 0, 128), pattern(intensity = 0.5f).toWaveform(true).amplitudes)
    }

    @Test
    fun `without amplitude control every pulse uses the default amplitude`() {
        val w = pattern(intensity = 0.3f, shape = PulseShape.SWELL).toWaveform(amplitudeControl = false)
        assertEquals(Waveform(listOf(0L, 100, 200, 100), listOf(0, -1, 0, -1)), w)
    }

    @Test
    fun `swell ramps up in four steps`() {
        val w = pattern(pulses = 1, pulse = 400, shape = PulseShape.SWELL).toWaveform(true)
        assertEquals(Waveform(listOf(0L, 100, 100, 100, 100), listOf(0, 63, 127, 191, 255)), w)
    }

    @Test
    fun `swell on a quiet setting never reaches zero mid-pulse`() {
        val w = pattern(pulses = 1, pulse = 400, intensity = 0.1f, shape = PulseShape.SWELL).toWaveform(true)
        assertTrue(w.amplitudes.drop(1).all { it >= 1 })
    }

    @Test
    fun `repeats are separated by the repeat gap`() {
        val w = pattern(pulses = 1, repeats = 2).toWaveform(true)
        assertEquals(Waveform(listOf(0L, 100, VibrationPattern.REPEAT_GAP_MILLIS, 100), listOf(0, 255, 0, 255)), w)
    }

    @Test
    fun `the total is capped at six seconds`() {
        val w = pattern(pulses = 6, pulse = 1500, gap = 1500, repeats = 3).toWaveform(true)
        assertEquals(VibrationPattern.MAX_TOTAL_MILLIS, w.totalMillis)
        assertEquals(w.timings.size, w.amplitudes.size)
        // Still alternating off/on after the cut.
        w.amplitudes.forEachIndexed { i, a -> assertEquals("segment $i", i % 2 == 0, a == 0) }
    }

    @Test
    fun `out of range values are clamped`() {
        val p = pattern(pulses = 20, pulse = 5, gap = 9_999, intensity = 3f, repeats = 0).sanitized()
        assertEquals(pattern(pulses = 6, pulse = 50, gap = 1500, intensity = 1f, repeats = 1), p)
        assertEquals(1f, pattern(intensity = Float.NaN).sanitized().intensity)
        assertEquals(VibrationPattern.MIN_INTENSITY, pattern(intensity = -1f).sanitized().intensity)
    }

    @Test
    fun `every preset fits within the cap and alternates`() {
        VibrationPreset.entries.forEach { preset ->
            listOf(true, false).forEach { amp ->
                val w = preset.pattern.toWaveform(amp)
                assertTrue("$preset", w.totalMillis <= VibrationPattern.MAX_TOTAL_MILLIS)
                assertEquals("$preset", preset.pattern, preset.pattern.sanitized())
                assertEquals(0, w.amplitudes.first())
            }
        }
    }

    @Test
    fun `presets are recognised and edits become custom`() {
        assertEquals(VibrationPreset.HEARTBEAT, VibrationPreset.of(VibrationPreset.HEARTBEAT.pattern))
        assertNull(VibrationPreset.of(VibrationPreset.HEARTBEAT.pattern.copy(pulses = 3)))
    }
}
