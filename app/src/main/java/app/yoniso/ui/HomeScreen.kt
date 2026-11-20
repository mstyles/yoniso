package app.yoniso.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yoniso.R

/** The published privacy policy (PRIVACY.md in the repository). Opened in the browser. */
private const val PRIVACY_POLICY_URL = "https://github.com/mstyles/yoniso/blob/main/PRIVACY.md"

/** A feature's state, as its home tile shows it. */
enum class FeatureState(val label: String) {
    ON("On"),
    OFF("Off"),

    /** Switched on, but something it needs is missing. */
    NEEDS_ATTENTION("Needs access"),
}

/** The first screen: an icon tile per feature, each opening that feature's settings. */
@Composable
fun HomeScreen(
    pauseState: FeatureState,
    onPause: () -> Unit,
    bellsState: FeatureState,
    onBells: () -> Unit,
    onAbout: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "Yoniso",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FeatureTile("Pause before apps", R.drawable.ic_pause_feature, pauseState, onPause, Modifier.weight(1f))
                FeatureTile("Mindfulness bell", R.drawable.ic_bell_feature, bellsState, onBells, Modifier.weight(1f))
            }
            val uriHandler = LocalUriHandler.current
            Row {
                TextButton(onClick = onAbout) { Text("About") }
                TextButton(
                    onClick = {
                        // Throws if no browser is installed; there is nothing more useful to do then.
                        runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) }
                    },
                ) { Text("Privacy policy") }
            }
        }
    }
}

/**
 * A square tile with the feature's icon: filled when it's on, outlined and muted when it's off, with
 * a red dot when it needs attention. Tapping opens its settings. TalkBack reads e.g. "Pause before
 * apps, On".
 */
@Composable
private fun FeatureTile(
    title: String,
    @DrawableRes icon: Int,
    state: FeatureState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileModifier = modifier
        .aspectRatio(1f)
        .semantics { contentDescription = "$title, ${state.label}" }
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(64.dp),
                tint = if (state == FeatureState.OFF) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
            if (state == FeatureState.NEEDS_ATTENTION) {
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(16.dp).size(14.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                ) {}
            }
        }
    }
    if (state == FeatureState.OFF) {
        OutlinedCard(onClick = onClick, modifier = tileModifier) { content() }
    } else {
        Card(
            onClick = onClick,
            modifier = tileModifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { content() }
    }
}
