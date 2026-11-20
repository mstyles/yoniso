package app.yoniso.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yoniso.R

/**
 * Shown once, on first launch: what the two features are, with the icons their home tiles use.
 * Accessibility access isn't asked for here; the pause page asks, with its disclosure (PRD FR-6),
 * when the user sets the pause up, so someone who only wants bells is never asked.
 */
@Composable
fun IntroScreen(onGetStarted: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Welcome to Yoniso",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text("Two gentle ways to come back to the present.", style = MaterialTheme.typography.bodyLarge)
            }

            Feature(
                R.drawable.ic_pause_feature,
                "Pause before apps",
                "When you open an app you've chosen, a short breathing pause lets you decide whether you really want to use it.",
            )
            Feature(
                R.drawable.ic_bell_feature,
                "Mindfulness bells",
                "A bell rings now and then through the day, as a reminder to stop and breathe.",
            )

            Text(
                "Tap either tile on the next screen to set it up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
        }
    }
}

/** A feature's tile icon, its name and a sentence about it. */
@Composable
private fun Feature(@DrawableRes icon: Int, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            Modifier.size(64.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(40.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(description, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
