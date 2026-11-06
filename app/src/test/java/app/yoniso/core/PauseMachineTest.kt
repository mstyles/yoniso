package app.yoniso.core

import app.yoniso.core.PauseAction.CancelTimer
import app.yoniso.core.PauseAction.GoHome
import app.yoniso.core.PauseAction.HideOverlay
import app.yoniso.core.PauseAction.SaveCooldown
import app.yoniso.core.PauseAction.ShowChoice
import app.yoniso.core.PauseAction.ShowOverlay
import app.yoniso.core.PauseAction.StartTimer
import app.yoniso.core.PauseEvent.CallStarted
import app.yoniso.core.PauseEvent.Continue
import app.yoniso.core.PauseEvent.Exit
import app.yoniso.core.PauseEvent.OverlayFailed
import app.yoniso.core.PauseEvent.OverlayShown
import app.yoniso.core.PauseEvent.ScreenOff
import app.yoniso.core.PauseEvent.ServiceStopped
import app.yoniso.core.PauseEvent.SettingsChanged
import app.yoniso.core.PauseEvent.TimerFinished
import app.yoniso.core.PauseEvent.WindowChanged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PauseMachineTest {

    private var now = 1_000_000L
    private val clock = Clock { now }

    private fun machine(
        settings: PauseSettings = PauseSettings(selectedPackages = setOf(A, B)),
        saved: Map<String, Long> = emptyMap(),
    ) = PauseMachine(settings, IGNORED, saved, clock)

    /** Shows the overlay for [pkg] and confirms it was added, as the service would. */
    private fun PauseMachine.pause(pkg: String) {
        assertEquals(listOf(ShowOverlay(pkg)), handle(WindowChanged(pkg)))
        handle(OverlayShown)
    }

    private fun PauseMachine.pauseUntilChoice(pkg: String) {
        pause(pkg)
        assertEquals(listOf(ShowChoice), handle(TimerFinished))
    }

    private fun minutes(n: Int) = n * 60_000L

    // Basic lifecycle

    @Test
    fun `selected app shows overlay, then starts timer and saves cooldown once shown`() {
        val m = machine()
        assertEquals(listOf(ShowOverlay(A)), m.handle(WindowChanged(A)))
        assertEquals(PauseState.PAUSING, m.state)
        assertEquals(A, m.pausedPackage)

        assertEquals(
            listOf(StartTimer(PauseDuration.TEN.millis), SaveCooldown(A, now)),
            m.handle(OverlayShown),
        )
    }

    @Test
    fun `timer finishing shows the choice`() {
        val m = machine()
        m.pause(A)
        assertEquals(listOf(ShowChoice), m.handle(TimerFinished))
        assertEquals(PauseState.AWAITING_CHOICE, m.state)
    }

    @Test
    fun `continue hides the overlay`() {
        val m = machine()
        m.pauseUntilChoice(A)
        assertEquals(listOf(HideOverlay), m.handle(Continue))
        assertEquals(PauseState.IDLE, m.state)
        assertNull(m.pausedPackage)
    }

    @Test
    fun `exit hides the overlay and goes home`() {
        val m = machine()
        m.pauseUntilChoice(A)
        assertEquals(listOf(HideOverlay, GoHome), m.handle(Exit))
        assertEquals(PauseState.IDLE, m.state)
    }

    @Test
    fun `continue and exit are ignored while the pause is running`() {
        val m = machine()
        m.pause(A)
        assertEquals(emptyList<PauseAction>(), m.handle(Continue))
        assertEquals(emptyList<PauseAction>(), m.handle(Exit))
        assertEquals(PauseState.PAUSING, m.state)
    }

    @Test
    fun `stray events in IDLE do nothing`() {
        val m = machine()
        for (event in listOf(TimerFinished, Continue, Exit, OverlayShown, OverlayFailed, ScreenOff, CallStarted, ServiceStopped)) {
            assertEquals(emptyList<PauseAction>(), m.handle(event))
            assertEquals(PauseState.IDLE, m.state)
        }
    }

    @Test
    fun `unselected app does not pause`() {
        val m = machine()
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(OTHER)))
    }

    @Test
    fun `disabled does not pause`() {
        val m = machine(PauseSettings(enabled = false, selectedPackages = setOf(A)))
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
    }

    @Test
    fun `pause length follows the setting`() {
        val m = machine(PauseSettings(selectedPackages = setOf(A), pauseDuration = PauseDuration.FIFTEEN))
        m.handle(WindowChanged(A))
        assertEquals(StartTimer(15_000), m.handle(OverlayShown).first())
    }

    @Test
    fun `failed overlay returns to idle without starting a cooldown`() {
        val m = machine()
        m.handle(WindowChanged(A))
        assertEquals(emptyList<PauseAction>(), m.handle(OverlayFailed))
        assertEquals(PauseState.IDLE, m.state)

        // Not cooling down: coming back to A tries again.
        m.handle(WindowChanged(HOME))
        assertEquals(listOf(ShowOverlay(A)), m.handle(WindowChanged(A)))
    }

    // Filtering

    @Test
    fun `duplicate events do not trigger a second overlay`() {
        val m = machine()
        m.pause(A)
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
        assertEquals(PauseState.PAUSING, m.state)
    }

    @Test
    fun `self, system UI and keyboards are ignored`() {
        val m = machine()
        m.pause(A)
        for (pkg in IGNORED) {
            assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(pkg)))
        }
        assertEquals(PauseState.PAUSING, m.state)
        assertEquals(A, m.pausedPackage)
    }

    @Test
    fun `ignored packages in between do not make the same app a new arrival`() {
        val m = machine(PauseSettings(selectedPackages = setOf(A), cooldownDuration = CooldownDuration.NONE))
        m.pauseUntilChoice(A)
        m.handle(Continue)
        m.handle(WindowChanged(SYSTEM_UI))
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
    }

    // Popups

    @Test
    fun `popup from an unselected app does not dismiss the pause`() {
        val m = machine()
        m.pause(A)
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(OTHER, isFullScreen = false)))
        assertEquals(PauseState.PAUSING, m.state)
        assertEquals(listOf(ShowChoice), m.handle(TimerFinished))
    }

    @Test
    fun `popup from an unselected app does not make the paused app a new arrival`() {
        // The phone bug: a Play Store popup over a game dismissed the pause, the game
        // reported itself again, and with no cooldown it paused again.
        val m = machine(PauseSettings(selectedPackages = setOf(A), cooldownDuration = CooldownDuration.NONE))
        m.pause(A)
        m.handle(WindowChanged(OTHER, isFullScreen = false))
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
        assertEquals(PauseState.PAUSING, m.state)
    }

    @Test
    fun `popup from an unselected app after continue does not undo the anti-loop rule`() {
        val m = machine(PauseSettings(selectedPackages = setOf(A), cooldownDuration = CooldownDuration.NONE))
        m.pauseUntilChoice(A)
        m.handle(Continue)
        m.handle(WindowChanged(OTHER, isFullScreen = false))
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
    }

    @Test
    fun `selected app's own non-full-screen window, like a splash screen, pauses`() {
        val m = machine()
        m.handle(WindowChanged(HOME))
        assertEquals(listOf(ShowOverlay(A)), m.handle(WindowChanged(A, isFullScreen = false)))
    }

    @Test
    fun `full-screen unselected app mid-pause still dismisses the overlay`() {
        val m = machine()
        m.pause(A)
        assertEquals(listOf(CancelTimer, HideOverlay), m.handle(WindowChanged(HOME, isFullScreen = true)))
        assertEquals(PauseState.IDLE, m.state)
    }

    // Anti-loop

    @Test
    fun `continue does not retrigger, even with no cooldown`() {
        val m = machine(PauseSettings(selectedPackages = setOf(A), cooldownDuration = CooldownDuration.NONE))
        m.pauseUntilChoice(A)
        m.handle(Continue)

        // More events from A, and the screen turning off and on again.
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
        m.handle(ScreenOff)
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
    }

    @Test
    fun `after continue, leaving to another app allows a pause again with no cooldown`() {
        val m = machine(PauseSettings(selectedPackages = setOf(A), cooldownDuration = CooldownDuration.NONE))
        m.pauseUntilChoice(A)
        m.handle(Continue)
        m.handle(WindowChanged(HOME))
        assertEquals(listOf(ShowOverlay(A)), m.handle(WindowChanged(A)))
    }

    // Cooldown

    @Test
    fun `returning during the cooldown does not pause`() {
        val m = machine()
        m.pauseUntilChoice(A)
        m.handle(Exit)
        m.handle(WindowChanged(HOME))

        now += minutes(4)
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
    }

    @Test
    fun `returning after the cooldown pauses again`() {
        val m = machine()
        m.pauseUntilChoice(A)
        m.handle(Exit)
        m.handle(WindowChanged(HOME))

        now += minutes(5)
        assertEquals(listOf(ShowOverlay(A)), m.handle(WindowChanged(A)))
    }

    @Test
    fun `cooldown starts when the overlay is shown, not when the choice is made`() {
        val m = machine()
        m.pause(A)
        now += minutes(3)
        m.handle(TimerFinished)
        m.handle(Exit)
        m.handle(WindowChanged(HOME))

        now += minutes(2)
        assertEquals(listOf(ShowOverlay(A)), m.handle(WindowChanged(A)))
    }

    @Test
    fun `cooldowns are per app`() {
        val m = machine()
        m.pauseUntilChoice(A)
        m.handle(Exit)
        m.handle(WindowChanged(HOME))
        assertEquals(listOf(ShowOverlay(B)), m.handle(WindowChanged(B)))
    }

    @Test
    fun `saved cooldowns are respected after a restart`() {
        val m = machine(saved = mapOf(A to now - minutes(1)))
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
    }

    @Test
    fun `saved cooldown in the future is treated as expired`() {
        val m = machine(saved = mapOf(A to now + minutes(60)))
        assertEquals(listOf(ShowOverlay(A)), m.handle(WindowChanged(A)))
    }

    @Test
    fun `clock moving backwards after a pause does not block pauses`() {
        val m = machine()
        m.pauseUntilChoice(A)
        m.handle(Exit)
        m.handle(WindowChanged(HOME))

        now -= minutes(60)
        assertEquals(listOf(ShowOverlay(A)), m.handle(WindowChanged(A)))
    }

    @Test
    fun `cooldown setting change applies to existing timestamps`() {
        val m = machine()
        m.pauseUntilChoice(A)
        m.handle(Exit)
        m.handle(WindowChanged(HOME))

        now += minutes(10)
        m.handle(SettingsChanged(m.settings.copy(cooldownDuration = CooldownDuration.FIFTEEN)))
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
    }

    // Switching and interruptions

    @Test
    fun `switching app mid-pause hides the overlay`() {
        val m = machine()
        m.pause(A)
        assertEquals(listOf(CancelTimer, HideOverlay), m.handle(WindowChanged(HOME)))
        assertEquals(PauseState.IDLE, m.state)
        // A timer that fires late is ignored.
        assertEquals(emptyList<PauseAction>(), m.handle(TimerFinished))
    }

    @Test
    fun `switching to another selected app mid-pause replaces the overlay`() {
        val m = machine()
        m.pause(A)
        assertEquals(listOf(CancelTimer, HideOverlay, ShowOverlay(B)), m.handle(WindowChanged(B)))
        assertEquals(B, m.pausedPackage)
    }

    @Test
    fun `switching app while awaiting choice hides the overlay`() {
        val m = machine()
        m.pauseUntilChoice(A)
        assertEquals(listOf(HideOverlay), m.handle(WindowChanged(OTHER)))
        assertEquals(PauseState.IDLE, m.state)
    }

    @Test
    fun `rapid switching A to B to A leaves one overlay and respects cooldowns`() {
        val m = machine()
        m.pause(A)
        assertEquals(listOf(CancelTimer, HideOverlay, ShowOverlay(B)), m.handle(WindowChanged(B)))
        m.handle(OverlayShown)
        // A was shown moments ago, so it is cooling down: B's overlay goes, and nothing replaces it.
        assertEquals(listOf(CancelTimer, HideOverlay), m.handle(WindowChanged(A)))
        assertEquals(PauseState.IDLE, m.state)
    }

    @Test
    fun `rapid switching with no cooldown pauses each arrival once`() {
        val m = machine(PauseSettings(selectedPackages = setOf(A, B), cooldownDuration = CooldownDuration.NONE))
        m.pause(A)
        m.handle(WindowChanged(B))
        m.handle(OverlayShown)
        assertEquals(listOf(CancelTimer, HideOverlay, ShowOverlay(A)), m.handle(WindowChanged(A)))
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
    }

    @Test
    fun `screen off mid-pause hides the overlay`() {
        val m = machine()
        m.pause(A)
        assertEquals(listOf(CancelTimer, HideOverlay), m.handle(ScreenOff))
        assertEquals(PauseState.IDLE, m.state)
    }

    @Test
    fun `unlocking back into the app within the cooldown does not pause`() {
        val m = machine()
        m.pause(A)
        m.handle(ScreenOff)
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
    }

    @Test
    fun `unlocking back into the app after the cooldown pauses`() {
        val m = machine()
        m.pause(A)
        m.handle(ScreenOff)
        now += minutes(5)
        assertEquals(listOf(ShowOverlay(A)), m.handle(WindowChanged(A)))
    }

    @Test
    fun `a call ringing mid-pause hides the overlay`() {
        val m = machine()
        m.pause(A)
        assertEquals(listOf(CancelTimer, HideOverlay), m.handle(CallStarted))
        assertEquals(PauseState.IDLE, m.state)
    }

    @Test
    fun `a call ringing while awaiting choice hides the overlay`() {
        val m = machine()
        m.pauseUntilChoice(A)
        assertEquals(listOf(HideOverlay), m.handle(CallStarted))
        assertEquals(PauseState.IDLE, m.state)
    }

    @Test
    fun `after a call is declined the app underneath is not paused again`() {
        val m = machine(PauseSettings(selectedPackages = setOf(A), cooldownDuration = CooldownDuration.NONE))
        m.pause(A)
        m.handle(CallStarted)
        // The heads-up call is system UI; afterwards A just reports itself again.
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(SYSTEM_UI)))
        assertEquals(emptyList<PauseAction>(), m.handle(WindowChanged(A)))
    }

    @Test
    fun `service stopped mid-pause cleans up`() {
        val m = machine()
        m.pause(A)
        assertEquals(listOf(CancelTimer, HideOverlay), m.handle(ServiceStopped))
        assertEquals(PauseState.IDLE, m.state)
        assertNull(m.pausedPackage)
    }

    @Test
    fun `service stopped while awaiting choice cleans up`() {
        val m = machine()
        m.pauseUntilChoice(A)
        assertEquals(listOf(HideOverlay), m.handle(ServiceStopped))
        assertEquals(PauseState.IDLE, m.state)
    }

    // Settings changes

    @Test
    fun `disabling mid-pause hides the overlay`() {
        val m = machine()
        m.pause(A)
        assertEquals(listOf(CancelTimer, HideOverlay), m.handle(SettingsChanged(m.settings.copy(enabled = false))))
    }

    @Test
    fun `deselecting the paused app hides the overlay`() {
        val m = machine()
        m.pauseUntilChoice(A)
        assertEquals(listOf(HideOverlay), m.handle(SettingsChanged(m.settings.copy(selectedPackages = setOf(B)))))
    }

    @Test
    fun `unrelated settings change mid-pause keeps the overlay`() {
        val m = machine()
        m.pause(A)
        assertEquals(
            emptyList<PauseAction>(),
            m.handle(SettingsChanged(m.settings.copy(pauseDuration = PauseDuration.FIVE))),
        )
        assertEquals(PauseState.PAUSING, m.state)
    }

    private companion object {
        const val A = "com.example.a"
        const val B = "com.example.b"
        const val OTHER = "com.example.other"
        const val HOME = "com.example.launcher"
        const val SELF = "app.yoniso"
        const val SYSTEM_UI = "com.android.systemui"
        const val IME = "com.example.keyboard"
        val IGNORED = setOf(SELF, SYSTEM_UI, IME)
    }
}
