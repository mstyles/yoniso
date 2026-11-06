package app.yoniso

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yoniso.data.InstalledApps
import app.yoniso.data.SettingsRepository
import app.yoniso.service.YonisoAccessibilityService
import app.yoniso.ui.AppListScreen
import app.yoniso.ui.OnboardingScreen
import app.yoniso.ui.SettingsScreen
import app.yoniso.ui.YonisoTheme
import kotlinx.coroutines.launch

/**
 * Hosts the three screens: first-launch onboarding, the main settings and the app list.
 * Settings are read from and written straight to [SettingsRepository]; the accessibility
 * service observes the same store.
 */
class MainActivity : ComponentActivity() {

    /** Re-checked in [onResume], since the user can turn the service off at any time. */
    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = SettingsRepository(this)
        val installedApps = InstalledApps(this)

        setContent {
            YonisoTheme {
                val onboardingDone by repository.onboardingDone.collectAsStateWithLifecycle(initialValue = null)
                val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
                var choosingApps by rememberSaveable { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                val loadedSettings = settings
                when {
                    // Still loading: a blank surface for a few milliseconds.
                    onboardingDone == null || loadedSettings == null -> Surface(Modifier.fillMaxSize()) {}

                    onboardingDone == false -> OnboardingScreen(
                        onOpenAccessibilitySettings = {
                            scope.launch { repository.setOnboardingDone() }
                            openAccessibilitySettings()
                        },
                        onNotNow = { scope.launch { repository.setOnboardingDone() } },
                    )

                    choosingApps -> AppListScreen(
                        installedApps = installedApps,
                        selectedPackages = loadedSettings.selectedPackages,
                        onSelectedChange = { pkg, selected -> scope.launch { repository.setSelected(pkg, selected) } },
                        onBack = { choosingApps = false },
                    )

                    else -> SettingsScreen(
                        settings = loadedSettings,
                        serviceEnabled = serviceEnabled,
                        onEnabledChange = { scope.launch { repository.setEnabled(it) } },
                        onChooseApps = { choosingApps = true },
                        onPauseDurationChange = { scope.launch { repository.setPauseDuration(it) } },
                        onCooldownDurationChange = { scope.launch { repository.setCooldownDuration(it) } },
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = isServiceEnabled()
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
