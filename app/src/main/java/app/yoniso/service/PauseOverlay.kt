package app.yoniso.service

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos

/** Muted greens and warm neutrals, light and dark, matching the app's theme. */
private class OverlayColors(
    val background: Color,
    val circle: Color,
    /** The lighter middle of the circle's gradient. */
    val circleHighlight: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
)

private val LightOverlay = OverlayColors(
    background = Color(0xFFEDEAE4),
    circle = Color(0xFFC9D3CB),
    circleHighlight = Color(0xFFDDE4DE),
    text = Color(0xFF3A3A38),
    secondaryText = Color(0xFF6B6A66),
    accent = Color(0xFF55685A),
    onAccent = Color.White,
    outline = Color(0xFFB9B6AE),
)

private val DarkOverlay = OverlayColors(
    background = Color(0xFF1C1C1A),
    circle = Color(0xFF34443A),
    circleHighlight = Color(0xFF465A4D),
    text = Color(0xFFE6E3DD),
    secondaryText = Color(0xFFADAAA3),
    accent = Color(0xFFAFC4B3),
    onAccent = Color(0xFF1C2B20),
    outline = Color(0xFF5E5D59),
)

/** How long the overlay takes to fade in, and the pause text to give way to the choice. */
private const val FADE_MILLIS = 600

/**
 * The mindfulness overlay. The pause timer runs in the service; [showChoice] flips
 * when it finishes. With reduced motion, nothing moves or fades: the circle is static
 * and changes are instant.
 */
@Composable
internal fun PauseOverlay(
    showChoice: Boolean,
    bellRinging: Boolean,
    /** Once the bell has been asked for, its verse stays for the rest of this pause. */
    showBellVerse: Boolean,
    reducedMotion: Boolean,
    onContinue: () -> Unit,
    onListenToBell: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) DarkOverlay else LightOverlay

    // The window appears at once (it must block touches immediately), so its contents fade in.
    val contentAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { contentAlpha.animateTo(1f, tween(FADE_MILLIS)) }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .semantics { paneTitle = "Mindful pause" },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha.value }
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BreathingCircle(colors = colors, animate = !reducedMotion)
                Spacer(Modifier.height(32.dp))

                AnimatedContent(
                    targetState = showChoice,
                    transitionSpec = {
                        if (reducedMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(tween(FADE_MILLIS, delayMillis = FADE_MILLIS / 2)) togetherWith
                                fadeOut(tween(FADE_MILLIS / 2))
                        }
                    },
                    // A fixed minimum height keeps the circle still when the text changes.
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp)
                        // Read out when the pause gives way to the choice.
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.TopCenter,
                    label = "pauseToChoice",
                ) { choice ->
                    if (choice) {
                        Choice(colors, bellRinging, showBellVerse, reducedMotion, onContinue, onListenToBell, onExit)
                    } else {
                        Breathe(colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun Breathe(colors: OverlayColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Take a breath.",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Notice this moment.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Choice(
    colors: OverlayColors,
    bellRinging: Boolean,
    showBellVerse: Boolean,
    reducedMotion: Boolean,
    onContinue: () -> Unit,
    onListenToBell: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        Modifier.widthIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BellVerse(colors, visible = showBellVerse, reducedMotion = reducedMotion)
        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue mindfully") }
        OutlinedButton(
            onClick = onListenToBell,
            // Disabled while it rings, so a second tap doesn't restart it.
            enabled = !bellRinging,
            border = BorderStroke(1.dp, colors.outline),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Listen to a bell", color = if (bellRinging) colors.secondaryText else colors.accent) }
        OutlinedButton(
            onClick = onExit,
            border = BorderStroke(1.dp, colors.outline),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Exit", color = colors.accent) }
    }
}

// One breath: in, hold, out, hold. Ten seconds, so the default pause is exactly one breath.
private const val INHALE_MILLIS = 4_000f
private const val HOLD_IN_MILLIS = 1_000f
private const val EXHALE_MILLIS = 4_000f
private const val HOLD_OUT_MILLIS = 1_000f
private const val EXHALE_START = INHALE_MILLIS + HOLD_IN_MILLIS
private const val CYCLE_MILLIS = EXHALE_START + EXHALE_MILLIS + HOLD_OUT_MILLIS

/** As each out-breath ends, a ripple (like the app icon's rings) spreads out and fades over this long. */
private const val RIPPLE_START = EXHALE_START + EXHALE_MILLIS
private const val RIPPLE_MILLIS = 4_200f

private val CANVAS_SIZE = 250.dp
private val MIN_RADIUS = 56.dp
private val MAX_RADIUS = 90.dp
private val RIPPLE_END_RADIUS = 120.dp
/** The soft halo reaches this far beyond the circle's edge, as a multiple of its radius. */
private const val GLOW_SPREAD = 1.35f

/** How full the breath is (0 empty, 1 full) at [t] milliseconds into the cycle. */
private fun breathAt(t: Float): Float = when {
    t < INHALE_MILLIS -> easeInOut(t / INHALE_MILLIS)
    t < EXHALE_START -> 1f
    t < EXHALE_START + EXHALE_MILLIS -> 1f - easeInOut((t - EXHALE_START) / EXHALE_MILLIS)
    else -> 0f
}

private fun easeInOut(x: Float): Float = (1f - cos(PI.toFloat() * x)) / 2f

private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x)

/**
 * The breathing circle: a softly lit disc that fills and empties with the breath,
 * glowing a little brighter when full, and sending out a ripple as each out-breath ends.
 * Decorative, so no semantics.
 * Without [animate], it shows the app icon's still form: the disc and two faint rings.
 */
@Composable
private fun BreathingCircle(
    colors: OverlayColors,
    animate: Boolean,
) {
    // One linear clock for the whole cycle; everything else is derived from it while drawing,
    // so the animation redraws without recomposing.
    val cycle = if (animate) {
        rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0f,
            targetValue = CYCLE_MILLIS,
            animationSpec = infiniteRepeatable(tween(CYCLE_MILLIS.toInt(), easing = LinearEasing)),
            label = "breathCycle",
        )
    } else {
        null
    }

    Canvas(Modifier.size(CANVAS_SIZE)) {
        val minRadius = MIN_RADIUS.toPx()
        val maxRadius = MAX_RADIUS.toPx()
        val t = cycle?.value
        val breath = if (t == null) 0.5f else breathAt(t)
        val radius = lerp(minRadius, maxRadius, breath)

        // Halo, brighter on a full breath.
        drawCircle(
            brush = Brush.radialGradient(
                (1f / GLOW_SPREAD) to colors.circle.copy(alpha = 0.25f + 0.35f * breath),
                1f to Color.Transparent,
                center = center,
                radius = radius * GLOW_SPREAD,
            ),
            radius = radius * GLOW_SPREAD,
        )

        // The disc, lit from the middle.
        drawCircle(
            brush = Brush.radialGradient(
                0f to colors.circleHighlight,
                1f to colors.circle,
                center = center,
                radius = radius,
            ),
            radius = radius,
        )

        if (t != null) {
            // Lasts into the next in-breath, so its age wraps around the cycle.
            val age = ((t - RIPPLE_START) % CYCLE_MILLIS + CYCLE_MILLIS) % CYCLE_MILLIS
            if (age < RIPPLE_MILLIS) {
                val p = age / RIPPLE_MILLIS
                drawCircle(
                    color = colors.accent,
                    radius = lerp(minRadius, RIPPLE_END_RADIUS.toPx(), easeOut(p)),
                    alpha = 0.6f * (1f - p),
                    style = Stroke(width = lerp(3.dp.toPx(), 1.dp.toPx(), p)),
                )
            }
        } else {
            drawCircle(colors.accent, radius * 1.28f, alpha = 0.35f, style = Stroke(2.dp.toPx()))
            drawCircle(colors.accent, radius * 1.55f, alpha = 0.18f, style = Stroke(2.dp.toPx()))
        }
    }
}

/** The verse that goes with the bell. Its space is always kept, so the buttons don't move when it appears. */
@Composable
private fun BellVerse(colors: OverlayColors, visible: Boolean, reducedMotion: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(FADE_MILLIS * 2)),
        ) {
            Text(
                "Listen, listen, this wonderful sound brings me back to my true home",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
