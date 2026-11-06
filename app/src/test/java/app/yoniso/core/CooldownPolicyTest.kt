package app.yoniso.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownPolicyTest {

    private val fiveMinutes = CooldownDuration.FIVE.millis

    @Test
    fun `never shown is not cooling down`() {
        assertFalse(CooldownPolicy.isCoolingDown(null, now = 1_000, cooldownMillis = fiveMinutes))
    }

    @Test
    fun `within the cooldown is cooling down`() {
        assertTrue(CooldownPolicy.isCoolingDown(0, now = fiveMinutes - 1, cooldownMillis = fiveMinutes))
    }

    @Test
    fun `cooldown expires exactly at its length`() {
        assertFalse(CooldownPolicy.isCoolingDown(0, now = fiveMinutes, cooldownMillis = fiveMinutes))
    }

    @Test
    fun `no cooldown never cools down`() {
        assertFalse(CooldownPolicy.isCoolingDown(1_000, now = 1_000, cooldownMillis = CooldownDuration.NONE.millis))
    }

    @Test
    fun `timestamp in the future counts as expired`() {
        assertFalse(CooldownPolicy.isCoolingDown(10_000, now = 1_000, cooldownMillis = fiveMinutes))
    }

    @Test
    fun `shown at this exact moment is cooling down`() {
        assertTrue(CooldownPolicy.isCoolingDown(1_000, now = 1_000, cooldownMillis = fiveMinutes))
    }
}
