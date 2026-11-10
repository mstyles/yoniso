package app.yoniso.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
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
 * While it shows, it silences the app underneath (see [PauseAudio]).
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY, which an AccessibilityService may add without the
 * "display over other apps" permission. Must be called on the main thread.
 */
internal class OverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private val audio = PauseAudio(context).also {
        // Created when the service connects: undo a mute left by a run that died mid-pause.
        it.restoreIfInterrupted()
    }

    private val bell = PauseBell(context)
    private val handler = Handler(Looper.getMainLooper())
    private val deferredHide = Runnable { hide() }

    /** The overlay is staying up only to cover the home transition after Exit; it is about to go. */
    private var leaving = false

    /** Whether the current overlay shows the choice. Reset for each new overlay. */
    private var showChoice by mutableStateOf(false)

    /** Whether the bell asked for from the choice is still ringing. */
    private var bellRinging by mutableStateOf(false)

    /** Whether the bell has been asked for during the current overlay, which shows its verse. */
    private var showBellVerse by mutableStateOf(false)

    /**
     * Adds the overlay with the breathing pause. Returns true if the overlay is showing
     * (including if it already was), false if the window could not be added.
     */
    fun show(onContinue: () -> Unit, onExit: () -> Unit): Boolean {
        if (view != null) {
            if (!leaving) return true
            // A new pause while the last one is still covering its exit: finish the exit now.
            hide()
        }

        showChoice = false
        showBellVerse = false
        val reducedMotion = isReducedMotion()
        val owner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PauseOverlay(
                    showChoice = showChoice,
                    bellRinging = bellRinging,
                    showBellVerse = showBellVerse,
                    reducedMotion = reducedMotion,
                    onContinue = onContinue,
                    onListenToBell = ::ringBell,
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
        ).apply {
            // Shown in the window list that accessibility services such as TalkBack read out.
            title = "Yoniso pause"
        }

        return try {
            windowManager.addView(composeView, params)
            view = composeView
            lifecycleOwner = owner
            audio.acquire()
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not add overlay", e)
            owner.destroy()
            false
        }
    }

    /** Switches the showing overlay from the pause to the choice. */
    fun showChoice() {
        if (view != null) showChoice = true
    }

    private fun ringBell() {
        if (view == null || leaving || bellRinging) return
        bellRinging = true
        showBellVerse = true
        bell.play(onFinished = { bellRinging = false })
    }

    /** Animations are switched off system-wide (the closest Android has to "reduce motion"). */
    private fun isReducedMotion(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /**
     * Removes the overlay once the home transition that has just been started is over, so the app
     * underneath never shows during it. The bell stops at once. [hide] still removes it immediately.
     */
    fun hideAfterHomeTransition() {
        if (view == null || leaving) return
        leaving = true
        bell.stop()
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        handler.postDelayed(deferredHide, (HOME_TRANSITION_MILLIS * scale).toLong() + SETTLE_MILLIS)
    }

    fun hide() {
        handler.removeCallbacks(deferredHide)
        leaving = false
        val current = view ?: return
        view = null
        // The bell belongs to the pause: leaving it (Continue, Exit or switching away) cuts it short.
        bell.stop()
        audio.release()
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

        /** Roughly how long Android's go-home animation runs at normal animation speed. */
        const val HOME_TRANSITION_MILLIS = 500L

        /** Extra time for the home screen to draw its first frame, also used with animations off. */
        const val SETTLE_MILLIS = 150L
    }
}
