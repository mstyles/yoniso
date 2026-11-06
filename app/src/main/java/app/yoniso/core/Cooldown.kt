package app.yoniso.core

/** Wall-clock time in milliseconds, as from `System.currentTimeMillis()`. Injected so tests control it. */
fun interface Clock {
    fun now(): Long
}

/**
 * Stores when a pause was last shown for each package. Wall-clock timestamps are used
 * (not `elapsedRealtime`, which resets on boot) so cooldowns survive reboots.
 */
interface CooldownStore {
    fun load(): Map<String, Long>
    fun save(packageName: String, shownAt: Long)
}

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
}
