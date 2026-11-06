package app.yoniso.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * First launch: explains why accessibility access is needed and what Yoniso does with it
 * (PRD FR-6), before sending the user to Android's accessibility settings.
 */
@Composable
fun OnboardingScreen(onOpenAccessibilitySettings: () -> Unit, onNotNow: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Welcome to Yoniso",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Yoniso offers a short breathing pause when you open an app you've chosen, " +
                    "so you can decide whether you really want to use it.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                "Why Yoniso needs accessibility access",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Disclosure(
                "Yoniso uses Android's accessibility service to notice when an app comes to the front. " +
                    "It only learns which app opened. It never reads what is on your screen or what you type.",
            )
            Disclosure("It uses this only to decide when to show the pause.")
            Disclosure(
                "Nothing is shared or sent anywhere. Yoniso has no internet access, and keeps only " +
                    "your settings and when each pause was last shown, on this phone.",
            )

            Text(
                "If you agree, the next screen is Android's accessibility settings. Tap Yoniso (it may be under \"Downloaded apps\"), then turn it on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                // Google Play requires an affirmative "agree" action after the disclosure.
                // "Not now" declines; the settings screen then repeats the disclosure.
                Text("Agree and open settings")
            }
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
                Text("Not now")
            }
        }
    }
}

@Composable
private fun Disclosure(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}
