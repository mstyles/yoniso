package app.yoniso.service

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Muted greens and warm neutrals, light and dark, matching the app's theme. */
private class OverlayColors(
    val background: Color,
    val circle: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
)

private val LightOverlay = OverlayColors(
    background = Color(0xFFEDEAE4),
    circle = Color(0xFFC9D3CB),
    text = Color(0xFF3A3A38),
    secondaryText = Color(0xFF6B6A66),
    accent = Color(0xFF55685A),
    onAccent = Color.White,
    outline = Color(0xFFB9B6AE),
)

private val DarkOverlay = OverlayColors(
    background = Color(0xFF1C1C1A),
    circle = Color(0xFF34443A),
    text = Color(0xFFE6E3DD),
    secondaryText = Color(0xFFADAAA3),
    accent = Color(0xFFAFC4B3),
    onAccent = Color(0xFF1C2B20),
    outline = Color(0xFF5E5D59),
)

/** One breath in or out, in milliseconds. */
private const val BREATH_MILLIS = 4_000

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
    reducedMotion: Boolean,
    onContinue: () -> Unit,
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
                BreathingCircle(color = colors.circle, animate = !reducedMotion)
                Spacer(Modifier.height(48.dp))

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
                        .heightIn(min = 140.dp)
                        // Read out when the pause gives way to the choice.
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.TopCenter,
                    label = "pauseToChoice",
                ) { choice ->
                    if (choice) Choice(colors, onContinue, onExit) else Breathe(colors)
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
private fun Choice(colors: OverlayColors, onContinue: () -> Unit, onExit: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Continue mindfully?",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onExit,
                border = BorderStroke(1.dp, colors.outline),
            ) { Text("Exit", color = colors.accent) }
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) { Text("Continue") }
        }
    }
}

/** A circle that slowly grows and shrinks, one breath each way. Decorative, so no semantics. */
@Composable
private fun BreathingCircle(color: Color, animate: Boolean) {
    val scale = if (animate) {
        val transition = rememberInfiniteTransition(label = "breath")
        val animated by transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(BREATH_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathScale",
        )
        animated
    } else {
        0.8f
    }
    Box(
        Modifier
            .size(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(color, CircleShape),
    )
}
