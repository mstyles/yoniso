package app.yoniso.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

/**
 * Phase 1 proof of concept: watches which app is in the foreground and shows the pause
 * overlay when the hardcoded target app comes to the front.
 *
 * The overlay triggers only when the foreground package changes. So after Continue,
 * further events from the same app are treated as duplicates and do not trigger it again.
 */
class YonisoAccessibilityService : AccessibilityService() {

    private lateinit var overlay: OverlayController
    private var ignoredPackages: Set<String> = emptySet()
    private var foregroundPackage: String? = null
    private var receiverRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Screen off: dismissing overlay")
            overlay.hide()
            // Treat the next app seen after unlocking as a fresh arrival.
            foregroundPackage = null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        ignoredPackages = buildIgnoredPackages()
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        Log.d(TAG, "Service connected; ignoring $ignoredPackages")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Our own overlay, the status bar/shade and keyboards are not app switches.
        if (pkg in ignoredPackages) return
        if (pkg == foregroundPackage) return

        Log.d(TAG, "Foreground: $foregroundPackage -> $pkg")
        foregroundPackage = pkg

        // The user moved to another app while paused (Home, Recents, a call, ...).
        overlay.hide()

        if (pkg == TARGET_PACKAGE) {
            Log.d(TAG, "Showing pause for $pkg")
            overlay.show(
                onContinue = {
                    Log.d(TAG, "Continue")
                    overlay.hide()
                },
                onExit = {
                    Log.d(TAG, "Exit")
                    overlay.hide()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                },
            )
        }
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

    private fun cleanUp() {
        if (::overlay.isInitialized) overlay.hide()
        if (receiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            receiverRegistered = false
        }
        foregroundPackage = null
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

        /** Phase 1: hardcoded test target. Replaced by user selection in Phase 3. */
        const val TARGET_PACKAGE = "com.android.chrome"
    }
}
