package app.yoniso.service

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
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

    val isShowing: Boolean get() = view != null

    fun show(onContinue: () -> Unit, onExit: () -> Unit) {
        if (view != null) return

        val owner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { PauseOverlay(onContinue = onContinue, onExit = onExit) }
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

        try {
            windowManager.addView(composeView, params)
            view = composeView
            lifecycleOwner = owner
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not add overlay", e)
            owner.destroy()
        }
    }

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
