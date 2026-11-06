package app.yoniso.core

enum class PauseState {
    /** No overlay. */
    IDLE,

    /** Overlay visible, pause timer running. */
    PAUSING,

    /** Pause finished; Continue and Exit are showing. */
    AWAITING_CHOICE,
}

/** Everything that can happen to the machine. The service turns Android callbacks into these. */
sealed interface PauseEvent {
    /**
     * A window-state event from [packageName]. May be a duplicate or an ignored package.
     * [isFullScreen] is false for dialogs and popups that don't fill the screen.
     */
    data class WindowChanged(val packageName: String, val isFullScreen: Boolean = true) : PauseEvent

    /** The overlay requested by [PauseAction.ShowOverlay] was added. */
    data object OverlayShown : PauseEvent

    /** The overlay requested by [PauseAction.ShowOverlay] could not be added. */
    data object OverlayFailed : PauseEvent

    data object TimerFinished : PauseEvent
    data object Continue : PauseEvent
    data object Exit : PauseEvent
    data object ScreenOff : PauseEvent
    data object ServiceStopped : PauseEvent
    data class SettingsChanged(val settings: PauseSettings) : PauseEvent
}

/** What the service must do in response. Carried out in order. */
sealed interface PauseAction {
    /** Add the overlay, then report [PauseEvent.OverlayShown] or [PauseEvent.OverlayFailed]. */
    data class ShowOverlay(val packageName: String) : PauseAction

    /** Switch the overlay from the breathing pause to Continue / Exit. */
    data object ShowChoice : PauseAction
    data object HideOverlay : PauseAction
    data object GoHome : PauseAction

    /** Report [PauseEvent.TimerFinished] after [millis]. */
    data class StartTimer(val millis: Long) : PauseAction
    data object CancelTimer : PauseAction

    /** Persist that a pause for [packageName] was shown at [shownAt]. */
    data class SaveCooldown(val packageName: String, val shownAt: Long) : PauseAction
}

/**
 * The interruption lifecycle: `IDLE → PAUSING → AWAITING_CHOICE → IDLE`.
 *
 * Pure and single-threaded: [handle] takes an event and returns the actions to carry out.
 * There is no separate DISMISSING state, because hiding the overlay is synchronous, so no
 * event can arrive while it is being removed.
 *
 * Rules:
 * - Popups (windows that don't fill the screen) from unselected apps are not app switches.
 * - Only one overlay at a time. A new foreground app hides any current overlay before
 *   the new app is considered.
 * - An app is paused only if Yoniso is enabled, the app is selected, its cooldown has
 *   expired, and it is not the app the user last chose to Continue into.
 * - After Continue, that app is not paused again until a different app has come to the
 *   front. This holds even with no cooldown, e.g. after the screen turns off and on.
 * - The cooldown starts when the overlay is actually shown.
 */
class PauseMachine(
    settings: PauseSettings,
    ignoredPackages: Set<String>,
    savedCooldowns: Map<String, Long>,
    private val clock: Clock,
) {
    var state: PauseState = PauseState.IDLE
        private set

    /** The app the current overlay belongs to, or null when [state] is IDLE. */
    var pausedPackage: String? = null
        private set

    var settings: PauseSettings = settings
        private set

    private val foreground = ForegroundTracker(ignoredPackages)
    private val lastShownAt = savedCooldowns.toMutableMap()

    /** The app the user continued into; not paused again until another app comes to the front. */
    private var continuedPackage: String? = null

    fun handle(event: PauseEvent): List<PauseAction> = when (event) {
        is PauseEvent.WindowChanged -> onWindowChanged(event.packageName, event.isFullScreen)
        PauseEvent.OverlayShown -> onOverlayShown()
        PauseEvent.OverlayFailed -> onOverlayFailed()
        PauseEvent.TimerFinished -> onTimerFinished()
        PauseEvent.Continue -> onContinue()
        PauseEvent.Exit -> onExit()
        PauseEvent.ScreenOff -> {
            foreground.reset()
            dismiss()
        }
        PauseEvent.ServiceStopped -> {
            foreground.reset()
            continuedPackage = null
            dismiss()
        }
        is PauseEvent.SettingsChanged -> onSettingsChanged(event.settings)
    }

    private fun onWindowChanged(packageName: String, isFullScreen: Boolean): List<PauseAction> {
        // A popup from an app that isn't selected (e.g. a Play Store prompt over a game) is not a
        // switch: the app underneath is still in front. A selected app's own small window, such
        // as its splash screen at cold start, still counts, since it may be the first sign of it.
        if (!isFullScreen && packageName !in settings.selectedPackages) return emptyList()
        if (!foreground.onWindowChanged(packageName)) return emptyList()

        val actions = dismiss().toMutableList()
        if (packageName != continuedPackage) continuedPackage = null
        if (shouldPause(packageName)) {
            state = PauseState.PAUSING
            pausedPackage = packageName
            actions += PauseAction.ShowOverlay(packageName)
        }
        return actions
    }

    private fun shouldPause(packageName: String): Boolean =
        settings.enabled &&
            packageName in settings.selectedPackages &&
            packageName != continuedPackage &&
            !CooldownPolicy.isCoolingDown(
                lastShownAt = lastShownAt[packageName],
                now = clock.now(),
                cooldownMillis = settings.cooldownDuration.millis,
            )

    private fun onOverlayShown(): List<PauseAction> {
        val packageName = pausedPackage
        if (state != PauseState.PAUSING || packageName == null) return emptyList()
        val now = clock.now()
        lastShownAt[packageName] = now
        return listOf(
            PauseAction.StartTimer(settings.pauseDuration.millis),
            PauseAction.SaveCooldown(packageName, now),
        )
    }

    private fun onOverlayFailed(): List<PauseAction> {
        if (state != PauseState.PAUSING) return emptyList()
        toIdle()
        return emptyList()
    }

    private fun onTimerFinished(): List<PauseAction> {
        if (state != PauseState.PAUSING) return emptyList()
        state = PauseState.AWAITING_CHOICE
        return listOf(PauseAction.ShowChoice)
    }

    private fun onContinue(): List<PauseAction> {
        if (state != PauseState.AWAITING_CHOICE) return emptyList()
        continuedPackage = pausedPackage
        toIdle()
        return listOf(PauseAction.HideOverlay)
    }

    private fun onExit(): List<PauseAction> {
        if (state != PauseState.AWAITING_CHOICE) return emptyList()
        toIdle()
        return listOf(PauseAction.HideOverlay, PauseAction.GoHome)
    }

    private fun onSettingsChanged(newSettings: PauseSettings): List<PauseAction> {
        settings = newSettings
        val paused = pausedPackage ?: return emptyList()
        return if (!newSettings.enabled || paused !in newSettings.selectedPackages) dismiss() else emptyList()
    }

    /** Removes any overlay and returns to IDLE. Safe to call in any state. */
    private fun dismiss(): List<PauseAction> {
        val actions = when (state) {
            PauseState.IDLE -> emptyList()
            PauseState.PAUSING -> listOf(PauseAction.CancelTimer, PauseAction.HideOverlay)
            PauseState.AWAITING_CHOICE -> listOf(PauseAction.HideOverlay)
        }
        toIdle()
        return actions
    }

    private fun toIdle() {
        state = PauseState.IDLE
        pausedPackage = null
    }
}
