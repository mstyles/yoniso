package app.yoniso.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Phase 1 pause length; becomes a setting in Phase 3. */
private const val PAUSE_SECONDS = 5

/** Phase 1 overlay: a plain countdown. The breathing animation comes in Phase 2. */
@Composable
internal fun PauseOverlay(onContinue: () -> Unit, onExit: () -> Unit) {
    var remaining by remember { mutableIntStateOf(PAUSE_SECONDS) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFEDEAE4))
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (remaining > 0) {
                Text("Take a breath.", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF3A3A38))
                Spacer(Modifier.height(16.dp))
                Text("$remaining", style = MaterialTheme.typography.titleLarge, color = Color(0xFF6B6A66))
            } else {
                Text("Continue mindfully?", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF3A3A38))
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onExit) { Text("Exit") }
                    Button(onClick = onContinue) { Text("Continue") }
                }
            }
        }
    }
}
