package app.yoniso

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yoniso.bells.BellAlarms
import app.yoniso.bells.BellVibrator
import app.yoniso.bells.CustomBellLibrary
import app.yoniso.core.BellSettings
import app.yoniso.data.BellRepository
import app.yoniso.data.InstalledApps
import app.yoniso.data.SettingsRepository
import app.yoniso.service.YonisoAccessibilityService
import app.yoniso.ui.AboutScreen
import app.yoniso.ui.AppListScreen
import app.yoniso.ui.BellEnvironment
import app.yoniso.ui.BellsScreen
import app.yoniso.ui.FeatureState
import app.yoniso.ui.HomeScreen
import app.yoniso.ui.IntroScreen
import app.yoniso.ui.PauseScreen
import app.yoniso.ui.YonisoTheme
import kotlinx.coroutines.launch

/**
 * Hosts the screens: the first-launch intro, home (a tile per feature), the pause settings and its
 * app list, the bells and About.
 * Settings are read from and written straight to [SettingsRepository] and [BellRepository]; the
 * accessibility service observes the same stores. Every bell change also resets the bell's alarm.
 */
class MainActivity : ComponentActivity() {

    /** Re-checked in [onResume], since the user can turn the service off at any time. */
    private var serviceEnabled by mutableStateOf(false)

    /** Also re-checked in [onResume]: the user can change any of it in system settings. */
    private var bellEnvironment by mutableStateOf(BellEnvironment())

    /** Asking for exact alarms after bells are turned on, once the notification question is answered. */
    private var askingForExactAlarms by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Granted or not, bells still ring; the notification only adds a Stop button.
        askForExactAlarmsIfNeeded()
    }

    private val bellRepository by lazy { BellRepository(this) }
    private val customBellLibrary by lazy { CustomBellLibrary(this, bellRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = SettingsRepository(this)
        // A safety net in case the bell's alarm was lost (e.g. exact alarms were revoked).
        if (savedInstanceState == null) BellAlarms.restoreAsync(this)
        val installedApps = InstalledApps(this)

        setContent {
            YonisoTheme {
                val onboardingDone by repository.onboardingDone.collectAsStateWithLifecycle(initialValue = null)
                val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
                var choosingApps by rememberSaveable { mutableStateOf(false) }
                var showingAbout by rememberSaveable { mutableStateOf(false) }
                var showingPause by rememberSaveable { mutableStateOf(false) }
                var showingBells by rememberSaveable { mutableStateOf(false) }
                val bellSettings by bellRepository.settings.collectAsStateWithLifecycle(initialValue = BellSettings())
                val nextRing by bellRepository.nextRingAt.collectAsStateWithLifecycle(initialValue = null)
                val customBells by customBellLibrary.bells.collectAsStateWithLifecycle(initialValue = emptyList())
                val is24Hour = DateFormat.is24HourFormat(this)
                val scope = rememberCoroutineScope()

                val loadedSettings = settings
                when {
                    // Still loading: a blank surface for a few milliseconds.
                    onboardingDone == null || loadedSettings == null -> Surface(Modifier.fillMaxSize()) {}

                    onboardingDone == false -> IntroScreen(
                        onGetStarted = { scope.launch { repository.setOnboardingDone() } },
                    )

                    choosingApps -> AppListScreen(
                        installedApps = installedApps,
                        selectedPackages = loadedSettings.selectedPackages,
                        onSelectedChange = { pkg, selected -> scope.launch { repository.setSelected(pkg, selected) } },
                        onBack = { choosingApps = false },
                    )

                    showingAbout -> AboutScreen(onBack = { showingAbout = false })

                    showingBells -> BellsScreen(
                        settings = bellSettings,
                        nextRing = nextRing,
                        environment = bellEnvironment,
                        customBells = customBells,
                        customBellLibrary = customBellLibrary,
                        is24Hour = is24Hour,
                        onEnabledChange = ::setBellsEnabled,
                        onChange = ::updateBells,
                        onAllowExactAlarms = ::openExactAlarmSettings,
                        onBack = { showingBells = false },
                    )

                    showingPause -> PauseScreen(
                        settings = loadedSettings,
                        serviceEnabled = serviceEnabled,
                        onEnabledChange = { scope.launch { repository.setEnabled(it) } },
                        onChooseApps = { choosingApps = true },
                        onPauseDurationChange = { scope.launch { repository.setPauseDuration(it) } },
                        onCooldownDurationChange = { scope.launch { repository.setCooldownDuration(it) } },
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onBack = { showingPause = false },
                    )

                    else -> HomeScreen(
                        pauseState = when {
                            !loadedSettings.enabled -> FeatureState.OFF
                            !serviceEnabled -> FeatureState.NEEDS_ATTENTION
                            else -> FeatureState.ON
                        },
                        onPause = { showingPause = true },
                        bellsState = if (bellSettings.enabled) FeatureState.ON else FeatureState.OFF,
                        onBells = { showingBells = true },
                        onAbout = { showingAbout = true },
                    )
                }

                if (askingForExactAlarms) {
                    AlertDialog(
                        onDismissRequest = { askingForExactAlarms = false },
                        title = { Text("Let bells ring on time?") },
                        text = {
                            Text(
                                "Android needs your permission for Yoniso to ring bells at their time. " +
                                    "Without it, bells may come several minutes late, and can only vibrate " +
                                    "while accessibility access is off. On the next screen, turn on " +
                                    "\"Allow setting alarms and reminders\".",
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                askingForExactAlarms = false
                                openExactAlarmSettings()
                            }) { Text("Open settings") }
                        },
                        dismissButton = { TextButton(onClick = { askingForExactAlarms = false }) { Text("Not now") } },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = isServiceEnabled()
        val audio = getSystemService(AudioManager::class.java)
        val filter = getSystemService(NotificationManager::class.java).currentInterruptionFilter
        bellEnvironment = BellEnvironment(
            exactAlarmsAllowed = BellAlarms.canRingOnTime(this),
            accessibilityOn = serviceEnabled,
            doNotDisturb = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            silent = audio.ringerMode == AudioManager.RINGER_MODE_SILENT,
            hasVibrator = BellVibrator.hasVibrator(this),
        )
    }

    /** Saves a bell change and resets the alarm to match. Runs on past this screen, so it always completes. */
    private fun updateBells(transform: (BellSettings) -> BellSettings) {
        val app = applicationContext
        BellAlarms.scope.launch {
            bellRepository.update(transform)
            BellAlarms.reschedule(app)
        }
    }

    /** Turning bells on asks for the notification permission, then for exact alarms, if missing. */
    private fun setBellsEnabled(enabled: Boolean) {
        updateBells { it.copy(enabled = enabled) }
        if (!enabled) return
        val needsNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotifications) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            askForExactAlarmsIfNeeded()
        }
    }

    private fun askForExactAlarmsIfNeeded() {
        if (!BellAlarms.canRingOnTime(this)) askingForExactAlarms = true
    }

    /** Yoniso's "Alarms & reminders" page (Android 12+). Exact alarms need no permission before that. */
    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, YonisoAccessibilityService::class.java)
        return getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == expected }
    }
}
