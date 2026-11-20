package app.yoniso.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.yoniso.bells.BellPlayer
import app.yoniso.bells.RunningState
import app.yoniso.core.BellSettings

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

    private val bell = BellPlayer(context)

    /** The bell chosen on the bells screen, and its volume. "Listen to a bell" plays it too. */
    var bellId: String = BellSettings.DEFAULT_BELL_ID
    var bellVolume: Float = 1f
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
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                LaunchedEffect(landscape) { coverSideCutout(this@apply, landscape) }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode = cutoutMode(landscape = false)
        }

        return try {
            windowManager.addView(composeView, params)
            view = composeView
            lifecycleOwner = owner
            RunningState.pauseShowing = true
            audio.acquire()
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not add overlay", e)
            owner.destroy()
            false
        }
    }

    /**
     * In landscape the camera cutout is on a side with no system bar, and by default the
     * window stops short of it, leaving a strip of the app showing, so the window extends
     * into cutouts on the short edges then. Not in portrait: there the cutout is in the status
     * bar, and extending into it would cover the status bar. Called again when the screen
     * rotates, e.g. when a landscape game finishes starting under the pause.
     */
    private fun coverSideCutout(view: ComposeView, landscape: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val mode = cutoutMode(landscape)
        if (params.layoutInDisplayCutoutMode == mode || !view.isAttachedToWindow) return
        params.layoutInDisplayCutoutMode = mode
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not update the overlay's cutout mode", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun cutoutMode(landscape: Boolean): Int =
        if (landscape) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }

    /** Switches the showing overlay from the pause to the choice. */
    fun showChoice() {
        if (view != null) showChoice = true
    }

    private fun ringBell() {
        if (view == null || leaving || bellRinging) return
        bellRinging = true
        showBellVerse = true
        bell.play(bellId, bellVolume, onFinished = { bellRinging = false })
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
        RunningState.pauseShowing = false
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
