package app.yoniso.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import app.yoniso.core.Clock
import app.yoniso.core.CooldownStore
import app.yoniso.core.PauseAction
import app.yoniso.core.PauseEvent
import app.yoniso.core.PauseMachine
import app.yoniso.core.PauseSettings
import app.yoniso.data.SharedPreferencesCooldownStore

/**
 * Thin adapter around [PauseMachine]: turns Android callbacks into [PauseEvent]s and
 * carries out the [PauseAction]s that come back. All decisions live in the machine.
 *
 * Everything runs on the main thread: accessibility events, the screen-off receiver,
 * overlay button clicks and the pause timer.
 */
class YonisoAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: OverlayController
    private lateinit var cooldownStore: CooldownStore
    private var machine: PauseMachine? = null
    private var receiverRegistered = false

    /** Events raised while actions are being carried out wait here, so the machine is never re-entered. */
    private val pendingEvents = ArrayDeque<PauseEvent>()
    private var dispatching = false

    private val timerFinished = Runnable { dispatch(PauseEvent.TimerFinished) }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            dispatch(PauseEvent.ScreenOff)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        cooldownStore = SharedPreferencesCooldownStore(this)
        val ignored = buildIgnoredPackages()
        machine = PauseMachine(
            settings = PHASE_2_SETTINGS,
            ignoredPackages = ignored,
            savedCooldowns = cooldownStore.load(),
            clock = Clock { System.currentTimeMillis() },
        )
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        Log.d(TAG, "Service connected; ignoring $ignored")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        dispatch(PauseEvent.WindowChanged(pkg))
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanUp()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanUp()
        super.onDestroy()
    }

    private fun dispatch(event: PauseEvent) {
        val machine = machine ?: return
        pendingEvents.addLast(event)
        if (dispatching) return
        dispatching = true
        try {
            while (pendingEvents.isNotEmpty()) {
                val next = pendingEvents.removeFirst()
                val actions = machine.handle(next)
                if (actions.isNotEmpty()) Log.d(TAG, "$next -> $actions (${machine.state})")
                actions.forEach(::perform)
            }
        } finally {
            dispatching = false
        }
    }

    private fun perform(action: PauseAction) {
        when (action) {
            is PauseAction.ShowOverlay -> {
                val shown = overlay.show(
                    onContinue = { dispatch(PauseEvent.Continue) },
                    onExit = { dispatch(PauseEvent.Exit) },
                )
                dispatch(if (shown) PauseEvent.OverlayShown else PauseEvent.OverlayFailed)
            }
            PauseAction.ShowChoice -> overlay.showChoice()
            PauseAction.HideOverlay -> overlay.hide()
            PauseAction.GoHome -> performGlobalAction(GLOBAL_ACTION_HOME)
            is PauseAction.StartTimer -> {
                handler.removeCallbacks(timerFinished)
                handler.postDelayed(timerFinished, action.millis)
            }
            PauseAction.CancelTimer -> handler.removeCallbacks(timerFinished)
            is PauseAction.SaveCooldown -> cooldownStore.save(action.packageName, action.shownAt)
        }
    }

    private fun cleanUp() {
        dispatch(PauseEvent.ServiceStopped)
        machine = null
        // Belt and braces: never leave a window or timer behind, whatever state the machine was in.
        handler.removeCallbacks(timerFinished)
        if (::overlay.isInitialized) overlay.hide()
        if (receiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            receiverRegistered = false
        }
    }

    private fun buildIgnoredPackages(): Set<String> {
        val imePackages = getSystemService(InputMethodManager::class.java)
            ?.inputMethodList
            ?.map { it.packageName }
            .orEmpty()
        return setOf(packageName, SYSTEM_UI_PACKAGE) + imePackages
    }

    private companion object {
        const val TAG = "Yoniso"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        /** Phase 2: hardcoded settings with Chrome as the target. Replaced by stored settings in Phase 3. */
        val PHASE_2_SETTINGS = PauseSettings(selectedPackages = setOf("com.android.chrome"))
    }
}
