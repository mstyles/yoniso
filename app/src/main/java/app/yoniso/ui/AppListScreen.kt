package app.yoniso.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.yoniso.R
import app.yoniso.data.AppEntry
import app.yoniso.data.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IconSize = 40.dp

/** Searchable list of launchable apps with a switch each (PRD FR-1). */
@Composable
fun AppListScreen(
    installedApps: InstalledApps,
    selectedPackages: Set<String>,
    onSelectedChange: (packageName: String, selected: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { installedApps.selectableApps() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    // Icons decoded so far, so scrolling back doesn't reload them. Only touched on the main thread.
    val iconCache = remember { HashMap<String, ImageBitmap>() }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
                Text(
                    "Choose apps",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            val all = apps
            if (all == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val trimmed = query.trim()
            val shown = if (trimmed.isEmpty()) all else all.filter { it.label.contains(trimmed, ignoreCase = true) }
            if (shown.isEmpty()) {
                Text(
                    "No apps match \"$trimmed\".",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        selected = app.packageName in selectedPackages,
                        onSelectedChange = { onSelectedChange(app.packageName, it) },
                        icon = { AppIcon(app.packageName, installedApps, iconCache) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.Switch, onValueChange = onSelectedChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = selected, onCheckedChange = null)
    }
}

/** Loads the icon off the main thread. Decorative: the row's label names the app. */
@Composable
private fun AppIcon(packageName: String, installedApps: InstalledApps, cache: HashMap<String, ImageBitmap>) {
    val sizePx = with(LocalDensity.current) { IconSize.roundToPx() }
    val bitmap by produceState(cache[packageName], packageName) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            installedApps.loadIcon(packageName)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
        }?.also { cache[packageName] = it }
    }
    val current = bitmap
    if (current != null) {
        Image(current, contentDescription = null, modifier = Modifier.size(IconSize))
    } else {
        Spacer(Modifier.size(IconSize))
    }
}
