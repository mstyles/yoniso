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
import app.yoniso.BuildConfig
import app.yoniso.MainActivity
import app.yoniso.core.Clock
import app.yoniso.core.PauseAction
import app.yoniso.core.PauseEvent
import app.yoniso.core.PauseMachine
import app.yoniso.data.InstalledApps
import app.yoniso.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Thin adapter around [PauseMachine]: turns Android callbacks into [PauseEvent]s and
 * carries out the [PauseAction]s that come back. All decisions live in the machine.
 *
 * Everything runs on the main thread: accessibility events, the screen-off receiver,
 * overlay button clicks, the pause timer and settings updates.
 *
 * The machine is created once the stored settings and cooldowns have loaded. Events
 * that arrive before then (a few milliseconds after connecting) are dropped.
 */
class YonisoAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: OverlayController
    private lateinit var repository: SettingsRepository
    private lateinit var calls: CallWatcher
    private var scope: CoroutineScope? = null
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
        // The system can reconnect a running service (e.g. when another accessibility client
        // binds). Tear down the previous connection first so no overlay, receiver or settings
        // observer is left behind.
        if (scope != null) {
            debugLog { "Reconnected without unbind; cleaning up the previous connection" }
            cleanUp()
        }
        overlay = OverlayController(this)
        calls = CallWatcher(this) { dispatch(PauseEvent.CallStarted) }.also { it.start() }
        repository = SettingsRepository(this)
        val ignored = buildIgnoredPackages()
        scope = MainScope().also { it.launch { observeSettings(ignored) } }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        debugLog { "Service connected; ignoring $ignored" }
    }

    /** Creates the machine from the first stored settings, then feeds it every change. */
    private suspend fun observeSettings(ignored: Set<String>) {
        val savedCooldowns = repository.loadCooldowns()
        val apps = InstalledApps(this)
        repository.settings
            // The app list never offers these, but a stored selection could predate a change
            // (e.g. a newly installed launcher), so they are removed here as well.
            .map { it.copy(selectedPackages = it.selectedPackages - apps.neverPausable()) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
            .collect { settings ->
                if (machine == null) {
                    machine = PauseMachine(settings, ignored, savedCooldowns, Clock { System.currentTimeMillis() })
                    debugLog { "Machine ready: $settings" }
                } else {
                    debugLog { "Settings changed: $settings" }
                    dispatch(PauseEvent.SettingsChanged(settings))
                }
            }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Yoniso's own windows: the overlay must not count as leaving the paused app, but the
        // settings screen is a real app switch. Without that, Chrome → Yoniso → Chrome would
        // look like Chrome never left, so choosing Chrome in Yoniso and going back wouldn't pause.
        if (pkg == packageName && event.className?.toString() != MainActivity::class.java.name) return
        dispatch(PauseEvent.WindowChanged(pkg, isFullScreen = event.isFullScreen))
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        debugLog { "Service unbound" }
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
                if (actions.isNotEmpty()) debugLog { "$next -> $actions (${machine.state})" }
                actions.forEach(::perform)
            }
        } finally {
            dispatching = false
        }
    }

    private fun perform(action: PauseAction) {
        when (action) {
            is PauseAction.ShowOverlay -> {
                // Never cover a ringing or ongoing call; the app opens without a pause.
                val shown = !calls.isCallActive() && overlay.show(
                    onContinue = { dispatch(PauseEvent.Continue) },
                    onExit = { dispatch(PauseEvent.Exit) },
                )
                dispatch(if (shown) PauseEvent.OverlayShown else PauseEvent.OverlayFailed)
            }
            PauseAction.ShowChoice -> overlay.showChoice()
            PauseAction.HideOverlay -> overlay.hide()
            PauseAction.ExitToHome -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                overlay.hideAfterHomeTransition()
            }
            is PauseAction.StartTimer -> {
                handler.removeCallbacks(timerFinished)
                handler.postDelayed(timerFinished, action.millis)
            }
            PauseAction.CancelTimer -> handler.removeCallbacks(timerFinished)
            is PauseAction.SaveCooldown -> scope?.launch { repository.saveCooldown(action.packageName, action.shownAt) }
        }
    }

    private fun cleanUp() {
        dispatch(PauseEvent.ServiceStopped)
        scope?.cancel()
        scope = null
        machine = null
        // Belt and braces: never leave a window or timer behind, whatever state the machine was in.
        handler.removeCallbacks(timerFinished)
        if (::overlay.isInitialized) overlay.hide()
        if (::calls.isInitialized) calls.stop()
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
        // Yoniso's own package is filtered in onAccessibilityEvent instead, and is never pausable.
        return setOf(SYSTEM_UI_PACKAGE) + imePackages
    }

    /** Debug builds only: release builds never log which apps were opened. */
    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    private companion object {
        const val TAG = "Yoniso"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
