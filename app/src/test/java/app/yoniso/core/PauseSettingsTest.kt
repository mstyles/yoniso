package app.yoniso.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseSettingsTest {

    @Test
    fun `stored pause seconds map back to their option`() {
        PauseDuration.entries.forEach { assertEquals(it, PauseDuration.fromSeconds(it.seconds)) }
    }

    @Test
    fun `missing or unknown pause seconds fall back to the default`() {
        assertEquals(PauseDuration.DEFAULT, PauseDuration.fromSeconds(null))
        assertEquals(PauseDuration.DEFAULT, PauseDuration.fromSeconds(7))
    }

    @Test
    fun `stored cooldown minutes map back to their option`() {
        CooldownDuration.entries.forEach { assertEquals(it, CooldownDuration.fromMinutes(it.minutes)) }
    }

    @Test
    fun `missing or unknown cooldown minutes fall back to the default`() {
        assertEquals(CooldownDuration.DEFAULT, CooldownDuration.fromMinutes(null))
        assertEquals(CooldownDuration.DEFAULT, CooldownDuration.fromMinutes(60))
    }
}
