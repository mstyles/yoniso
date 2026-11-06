package app.yoniso.core

/**
 * Turns raw window-state events into foreground app changes.
 *
 * Events from [ignoredPackages] (system UI, keyboards) are not app switches. Repeated events from the current foreground app are duplicates.
 * Anything else, including the home screen, counts as a new foreground app.
 */
class ForegroundTracker(private val ignoredPackages: Set<String>) {
    var current: String? = null
        private set

    /** Returns true if [packageName] has just come to the front. */
    fun onWindowChanged(packageName: String): Boolean {
        if (packageName in ignoredPackages || packageName == current) return false
        current = packageName
        return true
    }

    /** Forget the current app, so the next app seen counts as a new arrival (e.g. after screen off). */
    fun reset() {
        current = null
    }
}
