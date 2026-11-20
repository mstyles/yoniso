package app.yoniso.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class BellSettingsTest {

    @Test
    fun `defaults are already sane`() {
        assertEquals(BellSettings(), BellSettings().sanitized())
    }

    @Test
    fun `an interval that isn't offered becomes the default`() {
        assertEquals(BellSettings.DEFAULT_INTERVAL_MINUTES, BellSettings(intervalMinutes = 7).sanitized().intervalMinutes)
        assertEquals(45, BellSettings(intervalMinutes = 45).sanitized().intervalMinutes)
    }

    @Test
    fun `random bounds are clamped and ordered`() {
        val s = BellSettings(randomMinMinutes = 1, randomMaxMinutes = 1_000).sanitized()
        assertEquals(BellSettings.RANDOM_MIN_MINUTES, s.randomMinMinutes)
        assertEquals(BellSettings.RANDOM_MAX_MINUTES, s.randomMaxMinutes)

        val swapped = BellSettings(randomMinMinutes = 30, randomMaxMinutes = 10).sanitized()
        assertEquals(30, swapped.randomMinMinutes)
        assertEquals(30, swapped.randomMaxMinutes)
    }

    @Test
    fun `volume is clamped`() {
        assertEquals(BellSettings.MIN_VOLUME, BellSettings(volume = 0f).sanitized().volume)
        assertEquals(1f, BellSettings(volume = 2f).sanitized().volume)
        assertEquals(BellSettings.DEFAULT_VOLUME, BellSettings(volume = Float.NaN).sanitized().volume)
    }

    @Test
    fun `active hours are cut to whole minutes`() {
        val s = BellSettings(activeHours = ActiveHours(LocalTime.of(8, 0, 30), LocalTime.of(21, 0, 59, 5))).sanitized()
        assertEquals(LocalTime.of(8, 0), s.activeHours.start)
        assertEquals(LocalTime.of(21, 0), s.activeHours.end)
    }
}
