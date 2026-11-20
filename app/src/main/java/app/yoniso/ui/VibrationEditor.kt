package app.yoniso.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.yoniso.bells.BellVibrator
import app.yoniso.core.PulseShape
import app.yoniso.core.VibrationPattern
import app.yoniso.core.VibrationPreset
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The bell's vibration: presets, an editor for a custom pattern, and a preview. Choosing a preset or
 * finishing an edit plays the new pattern, so the user feels what they chose. Strength and swell only
 * show when the phone can set vibration strength.
 */
@Composable
internal fun VibrationEditor(pattern: VibrationPattern, onChange: (VibrationPattern) -> Unit) {
    val context = LocalContext.current
    val amplitudeControl = remember { BellVibrator.hasAmplitudeControl(context) }
    val preset = VibrationPreset.of(pattern)
    var customising by rememberSaveable { mutableStateOf(preset == null) }

    fun choose(new: VibrationPattern) {
        onChange(new)
        BellVibrator.vibrate(context, new)
    }

    Choices(
        // "Custom" only appears once the pattern is edited; choosing a preset replaces it.
        options = if (preset == null) VibrationPreset.entries + null else VibrationPreset.entries,
        selected = preset,
        label = { it?.let(::presetLabel) ?: "Custom" },
        onSelect = { it?.let { p -> choose(p.pattern) } },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { BellVibrator.vibrate(context, pattern) }) { Text("Preview") }
        TextButton(onClick = { customising = !customising }) { Text(if (customising) "Hide details" else "Customise") }
    }
    Text(
        "Vibration follows your phone's notification vibration setting.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (customising) {
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Details(pattern, amplitudeControl, ::choose)
        }
    }
}

@Composable
private fun Details(pattern: VibrationPattern, amplitudeControl: Boolean, onChange: (VibrationPattern) -> Unit) {
    PatternSlider(
        label = "Pulses",
        value = pattern.pulses.toFloat(),
        range = VibrationPattern.PULSES_RANGE.first.toFloat()..VibrationPattern.PULSES_RANGE.last.toFloat(),
        steps = VibrationPattern.PULSES_RANGE.last - VibrationPattern.PULSES_RANGE.first - 1,
        format = { "${it.roundToInt()}" },
        onFinished = { onChange(pattern.copy(pulses = it.roundToInt())) },
    )
    PatternSlider(
        label = "Pulse length",
        value = pattern.pulseMillis.toFloat(),
        range = VibrationPattern.PULSE_MILLIS_RANGE.first.toFloat()..VibrationPattern.PULSE_MILLIS_RANGE.last.toFloat(),
        format = { millisLabel(roundTo10(it)) },
        onFinished = { onChange(pattern.copy(pulseMillis = roundTo10(it))) },
    )
    if (pattern.pulses > 1) {
        PatternSlider(
            label = "Gap between pulses",
            value = pattern.gapMillis.toFloat(),
            range = VibrationPattern.GAP_MILLIS_RANGE.first.toFloat()..VibrationPattern.GAP_MILLIS_RANGE.last.toFloat(),
            format = { millisLabel(roundTo10(it)) },
            onFinished = { onChange(pattern.copy(gapMillis = roundTo10(it))) },
        )
    }
    if (amplitudeControl) {
        PatternSlider(
            label = "Strength",
            value = pattern.intensity,
            range = VibrationPattern.MIN_INTENSITY..1f,
            format = { "${(it * 100).roundToInt()}%" },
            // Whole 5% steps, so a nudge doesn't store 0.6372.
            onFinished = { onChange(pattern.copy(intensity = (it * 20).roundToInt() / 20f)) },
        )
        Text("Each pulse", style = MaterialTheme.typography.bodyLarge)
        Choices(
            options = PulseShape.entries,
            selected = pattern.shape,
            label = { if (it == PulseShape.SWELL) "Swells" else "Steady" },
            onSelect = { onChange(pattern.copy(shape = it)) },
        )
    }
    Text("Repeat", style = MaterialTheme.typography.bodyLarge)
    Choices(
        options = VibrationPattern.REPEATS_RANGE.toList(),
        selected = pattern.repeats,
        label = {
            when (it) {
                1 -> "Once"
                2 -> "Twice"
                else -> "$it times"
            }
        },
        onSelect = { onChange(pattern.copy(repeats = it)) },
    )
    val seconds = minOf(pattern.fullMillis, VibrationPattern.MAX_TOTAL_MILLIS) / 1000f
    Text(
        "Lasts ${String.format(Locale.getDefault(), "%.1f", seconds)} seconds." +
            if (pattern.isCut) " Vibrations stop at 6 seconds, so the end is cut off." else "",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A labelled slider showing its value, saved when the thumb is let go. */
@Composable
private fun PatternSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onFinished: (Float) -> Unit,
    steps: Int = 0,
) {
    var current by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    Row {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(format(current), style = MaterialTheme.typography.bodyLarge)
    }
    Slider(
        value = current,
        onValueChange = { current = it },
        valueRange = range,
        steps = steps,
        onValueChangeFinished = { onFinished(current) },
        modifier = Modifier.semantics {
            contentDescription = label
            stateDescription = format(current)
        },
    )
}

private fun roundTo10(millis: Float): Int = (millis / 10).roundToInt() * 10

private fun millisLabel(millis: Int): String = "$millis ms"

private fun presetLabel(preset: VibrationPreset): String = when (preset) {
    VibrationPreset.GENTLE_TAP -> "Gentle tap"
    VibrationPreset.DOUBLE_TAP -> "Double tap"
    VibrationPreset.HEARTBEAT -> "Heartbeat"
    VibrationPreset.THREE_SLOW -> "Three slow"
    VibrationPreset.SWELL -> "Swell"
}
