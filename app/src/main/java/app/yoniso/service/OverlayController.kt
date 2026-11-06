package app.yoniso.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Owns the single mindfulness overlay window. Only one overlay exists at a time,
 * and [hide] is safe to call at any point, including when nothing is showing.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY, which an AccessibilityService may add without the
 * "display over other apps" permission. Must be called on the main thread.
 */
internal class OverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    /** Whether the current overlay shows Continue / Exit. Reset for each new overlay. */
    private var showChoice by mutableStateOf(false)

    /**
     * Adds the overlay with the breathing pause. Returns true if the overlay is showing
     * (including if it already was), false if the window could not be added.
     */
    fun show(onContinue: () -> Unit, onExit: () -> Unit): Boolean {
        if (view != null) return true

        showChoice = false
        val reducedMotion = isReducedMotion()
        val owner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PauseOverlay(
                    showChoice = showChoice,
                    reducedMotion = reducedMotion,
                    onContinue = onContinue,
                    onExit = onExit,
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Focusable and touchable (no NOT_FOCUSABLE / NOT_TOUCHABLE flags): the
            // overlay takes touches and Back so the app underneath can't be used.
            // Home and Recents still work, and leaving the app dismisses the overlay.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )

        return try {
            windowManager.addView(composeView, params)
            view = composeView
            lifecycleOwner = owner
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not add overlay", e)
            owner.destroy()
            false
        }
    }

    /** Switches the showing overlay from the pause to Continue / Exit. */
    fun showChoice() {
        if (view != null) showChoice = true
    }

    /** Animations are switched off system-wide (the closest Android has to "reduce motion"). */
    private fun isReducedMotion(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    fun hide() {
        val current = view ?: return
        view = null
        try {
            windowManager.removeViewImmediate(current)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay was already detached", e)
        }
        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    private companion object {
        const val TAG = "Yoniso"
    }
}
