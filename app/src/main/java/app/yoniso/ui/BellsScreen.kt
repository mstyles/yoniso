package app.yoniso.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yoniso.bells.CustomBellLibrary
import app.yoniso.core.BellOutput
import app.yoniso.core.BellProblem
import app.yoniso.core.BellProblems
import app.yoniso.core.BellSchedule
import app.yoniso.core.BellSettings
import app.yoniso.core.BellTiming
import app.yoniso.core.CustomBell
import app.yoniso.core.DuringMedia
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/** What the bells screen needs to know about the phone. Checked again whenever the app resumes. */
data class BellEnvironment(
    val exactAlarmsAllowed: Boolean = true,
    val accessibilityOn: Boolean = true,
    val doNotDisturb: Boolean = false,
    val silent: Boolean = false,
    val hasVibrator: Boolean = true,
)

/** Random gaps offered on the slider, in minutes. Stored values up to 240 still work. */
private const val SLIDER_MIN = 5f
private const val SLIDER_MAX = 120f
private const val SLIDER_STEP = 5f

/** The mindfulness bells: timing, hours, days, what a bell does, which bell and which vibration. */
@Composable
internal fun BellsScreen(
    settings: BellSettings,
    nextRing: Instant?,
    environment: BellEnvironment,
    customBells: List<CustomBell>,
    customBellLibrary: CustomBellLibrary,
    is24Hour: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onChange: ((BellSettings) -> BellSettings) -> Unit,
    onAllowExactAlarms: () -> Unit,
    onBack: () -> Unit,
) {
    SubScreen("Mindfulness bell", onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "A bell rings now and then through the day, as a reminder to stop and breathe.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = settings.enabled, role = Role.Switch, onValueChange = onEnabledChange),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ring bells", style = MaterialTheme.typography.titleMedium)
                    Text(
                        statusLine(settings, nextRing, is24Hour),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.padding(8.dp))
                Switch(checked = settings.enabled, onCheckedChange = null)
            }

            if (settings.enabled) Snooze(settings, onChange)

            val problems = BellProblems.of(
                settings,
                exactAlarmsAllowed = environment.exactAlarmsAllowed,
                accessibilityOn = environment.accessibilityOn,
                doNotDisturb = environment.doNotDisturb,
                silent = environment.silent,
            )
            Problems(problems, onAllowExactAlarms)

            HorizontalDivider()

            Section("Timing") {
                Choices(
                    options = BellTiming.entries,
                    selected = settings.timing,
                    label = { if (it == BellTiming.RANDOM) "At random" else "At set times" },
                    onSelect = { timing -> onChange { it.copy(timing = timing) } },
                )
                when (settings.timing) {
                    BellTiming.RANDOM -> RandomGap(settings, onChange)
                    BellTiming.INTERVAL -> {
                        Text(
                            "On the clock: every 15 minutes rings at :00, :15, :30 and :45.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Choices(
                            options = BellSettings.INTERVAL_OPTIONS,
                            selected = settings.intervalMinutes,
                            label = ::intervalLabel,
                            onSelect = { minutes -> onChange { it.copy(intervalMinutes = minutes) } },
                        )
                    }
                }
            }

            Section("Hours", description = "Bells only ring between these times.") {
                ActiveHoursPicker(settings, is24Hour, onChange)
            }

            Section("Days") {
                DayPicker(settings.activeHours.days) { days ->
                    onChange { it.copy(activeHours = it.activeHours.copy(days = days)) }
                }
            }

            Section("Each bell") {
                Choices(
                    options = BellOutput.entries,
                    selected = settings.output,
                    label = {
                        when (it) {
                            BellOutput.BELL -> "Rings"
                            BellOutput.VIBRATE -> "Vibrates"
                            BellOutput.BOTH -> "Rings and vibrates"
                        }
                    },
                    onSelect = { output -> onChange { it.copy(output = output) } },
                )
                Text(
                    "Bells ring in vibrate mode too. On silent or with Do Not Disturb on, they are skipped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (settings.output.sound) {
                Section("Bell") {
                    BellChooser(settings, customBells, customBellLibrary, onChange)
                }
            }

            if (settings.output.vibrate && environment.hasVibrator) {
                Section("Vibration") {
                    VibrationEditor(settings.vibration) { pattern -> onChange { it.copy(vibration = pattern) } }
                }
            }

            Section("While music or video plays") {
                Choices(
                    options = DuringMedia.entries,
                    selected = settings.duringMedia,
                    label = {
                        when (it) {
                            DuringMedia.VIBRATE -> "Vibrate only"
                            DuringMedia.RING -> "Ring as usual"
                            DuringMedia.SKIP -> "Skip the bell"
                        }
                    },
                    onSelect = { media -> onChange { it.copy(duringMedia = media) } },
                )
            }

            Text(
                "Bells also skip during calls, and while a pause is showing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Snooze buttons, or a way to end the snooze. */
@Composable
private fun Snooze(settings: BellSettings, onChange: ((BellSettings) -> BellSettings) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isSnoozed(settings)) {
            OutlinedButton(onClick = { onChange { it.copy(snoozedUntil = null) } }) { Text("End snooze") }
        } else {
            OutlinedButton(onClick = { onChange { it.copy(snoozedUntil = Instant.now() + BellSchedule.SNOOZE_HOUR) } }) {
                Text("Snooze 1 hour")
            }
            OutlinedButton(onClick = {
                onChange { it.copy(snoozedUntil = BellSchedule.snoozeUntilTomorrow(Instant.now(), it.activeHours, ZoneId.systemDefault())) }
            }) {
                Text("Snooze until tomorrow")
            }
        }
    }
}

private fun isSnoozed(settings: BellSettings): Boolean = settings.snoozedUntil?.let { it > Instant.now() } == true

/** The line under the switch: off, or when the next bell is due. */
private fun statusLine(settings: BellSettings, nextRing: Instant?, is24Hour: Boolean): String {
    if (!settings.enabled) return "Off. No bells ring."
    val state = if (isSnoozed(settings)) "Snoozed" else "On"
    nextRing ?: return "$state. No bell is due."
    val zone = ZoneId.systemDefault()
    val at = nextRing.atZone(zone)
    val today = LocalDate.now(zone)
    val time = formatTime(at.toLocalTime(), is24Hour)
    val day = when (at.toLocalDate()) {
        today -> ""
        today.plusDays(1) -> "tomorrow "
        else -> at.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " "
    }
    // Set times ring on the minute; random ones can be a little late without exact alarms, and
    // "around" keeps a little of the surprise.
    val around = if (settings.timing == BellTiming.RANDOM) "around " else "at "
    return "$state. Next bell $day$around$time."
}

/** Problems that stop bells ringing as set up. The exact-alarm ones come with a way to fix them. */
@Composable
private fun Problems(problems: List<BellProblem>, onAllowExactAlarms: () -> Unit) {
    problems.forEach { problem ->
        when (problem) {
            BellProblem.NO_DAYS -> Notice("No days are chosen, so no bells will ring.")
            BellProblem.DO_NOT_DISTURB -> Notice("Do Not Disturb is on, so bells are skipped for now.")
            BellProblem.SILENT -> Notice("Your phone is on silent, so bells are skipped for now.")
            BellProblem.VIBRATE_ONLY, BellProblem.MAY_BE_LATE -> Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Let Yoniso ring on time", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (problem == BellProblem.VIBRATE_ONLY) {
                            "Android needs your permission for bells to ring at their time. Without it, and " +
                                "with accessibility access off, bells can only vibrate, and may come late."
                        } else {
                            "Android needs your permission for bells to ring at their time. Without it, " +
                                "bells may come several minutes late."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onAllowExactAlarms) { Text("Open settings") }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/** A range slider for the random gap, saved when the thumb is let go. */
@Composable
private fun RandomGap(settings: BellSettings, onChange: ((BellSettings) -> BellSettings) -> Unit) {
    val stored = settings.randomMinMinutes.toFloat()..settings.randomMaxMinutes.toFloat()
    var range by remember(stored) {
        mutableStateOf(stored.start.coerceIn(SLIDER_MIN, SLIDER_MAX)..stored.endInclusive.coerceIn(SLIDER_MIN, SLIDER_MAX))
    }
    val min = range.start.roundToInt()
    val max = range.endInclusive.roundToInt()
    Text(
        if (min == max) "Every $min minutes" else "Every $min to $max minutes",
        style = MaterialTheme.typography.bodyLarge,
    )
    RangeSlider(
        value = range,
        onValueChange = { range = it },
        valueRange = SLIDER_MIN..SLIDER_MAX,
        steps = ((SLIDER_MAX - SLIDER_MIN) / SLIDER_STEP).toInt() - 1,
        onValueChangeFinished = {
            onChange { it.copy(randomMinMinutes = range.start.roundToInt(), randomMaxMinutes = range.endInclusive.roundToInt()) }
        },
        modifier = Modifier.semantics { contentDescription = "Time between bells, in minutes" },
    )
}

private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes == 60 -> "1 hour"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "${minutes / 60}½ hours"
}

/** From and to buttons, each opening a time picker. */
@Composable
private fun ActiveHoursPicker(settings: BellSettings, is24Hour: Boolean, onChange: ((BellSettings) -> BellSettings) -> Unit) {
    val hours = settings.activeHours
    var editing by remember { mutableStateOf<Boolean?>(null) } // true: start, false: end
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { editing = true }) { Text("From ${formatTime(hours.start, is24Hour)}") }
        OutlinedButton(onClick = { editing = false }) { Text("To ${formatTime(hours.end, is24Hour)}") }
    }
    if (hours.start == hours.end) {
        Text("The same start and end: bells ring all day and night.", style = MaterialTheme.typography.bodyMedium)
    } else if (hours.end < hours.start) {
        Text("Crosses midnight: ends the next morning.", style = MaterialTheme.typography.bodyMedium)
    }
    val start = editing ?: return
    TimeDialog(
        title = if (start) "Bells start at" else "Bells end at",
        initial = if (start) hours.start else hours.end,
        is24Hour = is24Hour,
        onDismiss = { editing = null },
        onConfirm = { time ->
            editing = null
            onChange {
                it.copy(activeHours = if (start) it.activeHours.copy(start = time) else it.activeHours.copy(end = time))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, is24Hour: Boolean, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A chip per day, in the order the user's locale starts the week. */
@Composable
private fun DayPicker(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val first = WeekFields.of(locale).firstDayOfWeek
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0L until 7L).map { first.plus(it) }.forEach { day ->
            val selected = day in days
            FilterChip(
                selected = selected,
                onClick = { onChange(if (selected) days - day else days + day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                },
            )
        }
    }
}

/** A time in the user's 12- or 24-hour style. */
internal fun formatTime(time: LocalTime, is24Hour: Boolean): String =
    time.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()))
