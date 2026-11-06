package app.yoniso.service

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val Background = Color(0xFFEDEAE4)
private val Circle = Color(0xFFC9D3CB)
private val TextPrimary = Color(0xFF3A3A38)
private val TextSecondary = Color(0xFF6B6A66)
private val Accent = Color(0xFF55685A)

/** One breath in or out, in milliseconds. */
private const val BREATH_MILLIS = 4_000

/**
 * The mindfulness overlay. The pause timer runs in the service; [showChoice] flips
 * when it finishes. With reduced motion, the breathing circle is static.
 */
@Composable
internal fun PauseOverlay(
    showChoice: Boolean,
    reducedMotion: Boolean,
    onContinue: () -> Unit,
    onExit: () -> Unit,
) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BreathingCircle(animate = !reducedMotion)
            Spacer(Modifier.height(48.dp))

            if (!showChoice) {
                Text(
                    "Take a breath.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Notice this moment.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    "Continue mindfully?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onExit) { Text("Exit", color = Accent) }
                    Button(
                        onClick = onContinue,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) { Text("Continue") }
                }
            }
        }
    }
}

/** A circle that slowly grows and shrinks, one breath each way. Decorative, so no semantics. */
@Composable
private fun BreathingCircle(animate: Boolean) {
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
            .background(Circle, CircleShape),
    )
}
