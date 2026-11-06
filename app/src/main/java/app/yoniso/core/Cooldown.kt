package app.yoniso.core

/** Wall-clock time in milliseconds, as from `System.currentTimeMillis()`. Injected so tests control it. */
fun interface Clock {
    fun now(): Long
}

/**
 * Cooldown timestamps are wall-clock times (not `elapsedRealtime`, which resets on boot)
 * so cooldowns survive reboots.
 */
object CooldownPolicy {
    /**
     * Whether an app whose pause was last shown at [lastShownAt] is still cooling down at [now].
     * A timestamp in the future means the clock was moved back, so it is treated as expired
     * rather than blocking pauses until the clock catches up.
     */
    fun isCoolingDown(lastShownAt: Long?, now: Long, cooldownMillis: Long): Boolean {
        if (lastShownAt == null || cooldownMillis <= 0) return false
        if (lastShownAt > now) return false
        return now - lastShownAt < cooldownMillis
    }

    /**
     * The timestamps still worth keeping at [now]: those that could still block a pause
     * under the longest cooldown option. Expired and future timestamps are dropped.
     */
    fun prune(lastShownAt: Map<String, Long>, now: Long): Map<String, Long> {
        val longest = CooldownDuration.entries.maxOf { it.millis }
        return lastShownAt.filterValues { isCoolingDown(it, now, longest) }
    }
}
