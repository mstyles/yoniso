package app.yoniso.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.yoniso.bells.BellPlayer
import app.yoniso.bells.BundledBell
import app.yoniso.bells.CustomBellFiles.ImportResult
import app.yoniso.bells.CustomBellLibrary
import app.yoniso.core.BellSettings
import app.yoniso.core.CustomBell
import app.yoniso.core.CustomBellProblem
import app.yoniso.core.CustomBellRules
import kotlinx.coroutines.launch

/** What a dialog over the bell list is doing. */
private sealed interface BellDialog {
    data class Name(val fileName: String, val suggested: String) : BellDialog
    data class Edit(val bell: CustomBell) : BellDialog
    data class ConfirmDelete(val bell: CustomBell) : BellDialog
    data class Refused(val problem: CustomBellProblem) : BellDialog
}

/** The bundled bells, the user's own, "Add your own…", a preview each, and the volume. */
@Composable
internal fun BellChooser(
    settings: BellSettings,
    customBells: List<CustomBell>,
    library: CustomBellLibrary,
    onChange: ((BellSettings) -> BellSettings) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { BellPlayer(context) }
    var playing by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player) { onDispose { player.stop() } }
    var volume by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
    var dialog by remember { mutableStateOf<BellDialog?>(null) }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            dialog = when (val result = library.import(uri)) {
                is ImportResult.Imported -> BellDialog.Name(result.fileName, result.suggestedName)
                is ImportResult.Refused -> BellDialog.Refused(result.problem)
            }
            importing = false
        }
    }

    fun togglePreview(id: String) {
        if (playing == id) {
            player.stop()
        } else {
            playing = id
            // A bell cut short by another must not clear the new one.
            player.play(id, volume) { if (playing == id) playing = null }
        }
    }

    Column(Modifier.selectableGroup()) {
        BundledBell.entries.forEach { bell ->
            BellRow(
                label = bell.label,
                selected = settings.bellId == bell.id,
                playing = playing == bell.id,
                onSelect = { onChange { it.copy(bellId = bell.id) } },
                onPreview = { togglePreview(bell.id) },
            )
        }
        customBells.forEach { bell ->
            BellRow(
                label = bell.name,
                selected = settings.bellId == bell.id,
                playing = playing == bell.id,
                onSelect = { onChange { it.copy(bellId = bell.id) } },
                onPreview = { togglePreview(bell.id) },
                onEdit = { dialog = BellDialog.Edit(bell) },
            )
        }
    }
    OutlinedButton(
        onClick = { picker.launch(arrayOf("audio/*")) },
        enabled = !importing,
    ) { Text(if (importing) "Adding…" else "Add your own…") }
    Text(
        "A sound file up to a minute long and 5 MB. Yoniso keeps its own copy, on this phone only.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("Volume", style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = volume,
        onValueChange = { volume = it },
        valueRange = BellSettings.MIN_VOLUME..1f,
        onValueChangeFinished = { onChange { it.copy(volume = volume) } },
        modifier = Modifier.semantics { contentDescription = "Bell volume" },
    )
    Text(
        "Bells follow your phone's alarm volume. This sets how loud they are within it.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when (val d = dialog) {
        null -> Unit
        is BellDialog.Name -> NameDialog(
            title = "Name your bell",
            initial = d.suggested,
            confirm = "Save",
            onConfirm = { name ->
                library.save(d.fileName, name)
                dialog = null
            },
            onDismiss = {
                library.discard(d.fileName)
                dialog = null
            },
        )
        is BellDialog.Edit -> NameDialog(
            title = "Rename bell",
            initial = d.bell.name,
            confirm = "Save",
            onConfirm = { name ->
                library.rename(d.bell, name)
                dialog = null
            },
            onDismiss = { dialog = null },
            onDelete = { dialog = BellDialog.ConfirmDelete(d.bell) },
        )
        is BellDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete \"${d.bell.name}\"?") },
            text = {
                Text(
                    if (settings.bellId == d.bell.id) {
                        "Yoniso's copy of the sound is deleted. Bells will ring the Plum Village bell instead."
                    } else {
                        "Yoniso's copy of the sound is deleted. The file you added it from isn't touched."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (playing == d.bell.id) player.stop()
                    library.delete(d.bell)
                    dialog = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        is BellDialog.Refused -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Can't use this sound") },
            text = { Text(refusalText(d.problem)) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
        )
    }
}

private fun refusalText(problem: CustomBellProblem): String = when (problem) {
    CustomBellProblem.TOO_LARGE -> "The file is larger than 5 MB. Choose a smaller one."
    CustomBellProblem.TOO_LONG -> "The sound is longer than a minute. Choose a shorter one."
    CustomBellProblem.UNREADABLE ->
        "Android can't play this file. Try a common sound format, such as MP3, M4A, OGG or WAV."
}

@Composable
private fun BellRow(
    label: String,
    selected: Boolean,
    playing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
        if (onEdit != null) {
            TextButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = "Rename or delete $label" },
            ) { Text("Edit") }
        }
        TextButton(
            onClick = onPreview,
            modifier = Modifier.semantics { contentDescription = if (playing) "Stop $label" else "Play $label" },
        ) { Text(if (playing) "Stop" else "Play") }
    }
}

/** Asks for a bell's name. Save is off while the name is blank. [onDelete] adds a Delete button. */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial) }
    val clean = CustomBellRules.cleanName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        // A stray tap outside mustn't throw away a bell just added.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(CustomBellRules.MAX_NAME_LENGTH) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { clean?.let(onConfirm) }, enabled = clean != null) { Text(confirm) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
