package app.yoniso.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yoniso.core.CooldownDuration
import app.yoniso.core.PauseDuration
import app.yoniso.core.PauseSettings

/** The published privacy policy (PRIVACY.md in the repository). Opened in the browser. */
private const val PRIVACY_POLICY_URL = "https://github.com/mstyles/yoniso/blob/main/PRIVACY.md"

/** The main screen (PRD FR-5). */
@Composable
fun SettingsScreen(
    settings: PauseSettings,
    serviceEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onChooseApps: () -> Unit,
    onPauseDurationChange: (PauseDuration) -> Unit,
    onCooldownDurationChange: (CooldownDuration) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Yoniso",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )

            AccessibilityStatus(serviceEnabled, onOpenAccessibilitySettings)

            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = settings.enabled, role = Role.Switch, onValueChange = onEnabledChange),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pause before selected apps", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.enabled) "On" else "Off. No pauses are shown.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.padding(8.dp))
                Switch(checked = settings.enabled, onCheckedChange = null)
            }

            HorizontalDivider()

            Section("Apps") {
                val count = settings.selectedPackages.size
                Text(
                    when (count) {
                        0 -> "No apps selected yet."
                        1 -> "1 app selected."
                        else -> "$count apps selected."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = onChooseApps) { Text("Choose apps") }
            }

            Section("Pause length", description = "How long to breathe before you can continue.") {
                Choices(
                    options = PauseDuration.entries,
                    selected = settings.pauseDuration,
                    label = { "${it.seconds} seconds" },
                    onSelect = onPauseDurationChange,
                )
            }

            Section(
                "Cooldown",
                description = "After a pause, the same app opens without another pause for this long.",
            ) {
                Choices(
                    options = CooldownDuration.entries,
                    selected = settings.cooldownDuration,
                    label = { if (it == CooldownDuration.NONE) "None" else "${it.minutes} min" },
                    onSelect = onCooldownDurationChange,
                )
            }

            HorizontalDivider()

            val uriHandler = LocalUriHandler.current
            TextButton(
                onClick = {
                    // Throws if no browser is installed; there is nothing more useful to do then.
                    runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) }
                },
            ) { Text("Privacy policy") }
        }
    }
}

@Composable
private fun AccessibilityStatus(serviceEnabled: Boolean, onOpenAccessibilitySettings: () -> Unit) {
    if (serviceEnabled) {
        Text(
            "Accessibility access is on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Accessibility access is off", style = MaterialTheme.typography.titleMedium)
            Text(
                "Yoniso uses Android's accessibility service to notice when a selected app opens, " +
                    "and uses that only to show the pause. It only sees which app opened, never what " +
                    "is on your screen or what you type, and nothing is shared or leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "In accessibility settings, tap Yoniso (it may be under \"Downloaded apps\"), then turn it on. " +
                    "If Android says the setting is restricted, open Yoniso's App info, tap the ⋮ menu, " +
                    "and choose \"Allow restricted settings\".",
                style = MaterialTheme.typography.bodyMedium,
            )
            // Google Play requires an explicit "agree" right after the disclosure above.
            Button(onClick = onOpenAccessibilitySettings) { Text("Agree and open settings") }
        }
    }
}

@Composable
private fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

/** A single-choice group of chips, announced as radio buttons. */
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(
        Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}
