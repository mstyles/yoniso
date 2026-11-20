package app.yoniso.bells

/**
 * What the rest of the process is doing, for the bell receiver to read. The accessibility service
 * and the bells share one process, so a plain flag is enough: if the process wasn't running, neither
 * was the service, and no pause was showing.
 *
 * Written on the main thread, read from the receiver's background thread.
 */
internal object RunningState {
    /**
     * The accessibility service is bound. While it is, Android lets Yoniso start the bell service
     * from the background, even from an inexact alarm.
     */
    @Volatile var accessibilityConnected = false

    /** A pause is on screen. It already is the reminder, so a bell skips. */
    @Volatile var pauseShowing = false
}
