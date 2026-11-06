package app.yoniso.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.yoniso.core.CooldownDuration
import app.yoniso.core.CooldownPolicy
import app.yoniso.core.PauseDuration
import app.yoniso.core.PauseSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// One instance of each per process. The app UI and the accessibility service share the process.
private val Context.settingsStore by preferencesDataStore(name = "settings")

// Kept apart from the settings, so recording a pause doesn't make settings observers re-emit.
private val Context.cooldownStore by preferencesDataStore(name = "cooldowns")

/**
 * The user's settings and the per-app cooldown timestamps, stored on the device with DataStore.
 * Unreadable data falls back to the defaults rather than crashing.
 */
class SettingsRepository(context: Context) {
    private val settingsStore = context.applicationContext.settingsStore
    private val cooldownStore = context.applicationContext.cooldownStore

    val settings: Flow<PauseSettings> = settingsStore.safeData()
        .map { prefs ->
            PauseSettings(
                enabled = prefs[ENABLED] ?: true,
                selectedPackages = prefs[SELECTED_PACKAGES].orEmpty(),
                pauseDuration = PauseDuration.fromSeconds(prefs[PAUSE_SECONDS]),
                cooldownDuration = CooldownDuration.fromMinutes(prefs[COOLDOWN_MINUTES]),
            )
        }
        .distinctUntilChanged()

    val onboardingDone: Flow<Boolean> = settingsStore.safeData()
        .map { it[ONBOARDING_DONE] ?: false }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        settingsStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setSelected(packageName: String, selected: Boolean) {
        settingsStore.edit {
            val current = it[SELECTED_PACKAGES].orEmpty()
            it[SELECTED_PACKAGES] = if (selected) current + packageName else current - packageName
        }
    }

    suspend fun setPauseDuration(duration: PauseDuration) {
        settingsStore.edit { it[PAUSE_SECONDS] = duration.seconds }
    }

    suspend fun setCooldownDuration(duration: CooldownDuration) {
        settingsStore.edit { it[COOLDOWN_MINUTES] = duration.minutes }
    }

    suspend fun setOnboardingDone() {
        settingsStore.edit { it[ONBOARDING_DONE] = true }
    }

    /** When each app's pause was last shown, keyed by package name. */
    suspend fun loadCooldowns(): Map<String, Long> = cooldownStore.safeData().first().timestamps()

    /** Records that [packageName]'s pause was shown at [shownAt], and drops timestamps that no longer matter. */
    suspend fun saveCooldown(packageName: String, shownAt: Long) {
        cooldownStore.edit { prefs ->
            val stored = prefs.timestamps()
            val keep = CooldownPolicy.prune(stored, now = shownAt)
            (stored.keys - keep.keys).forEach { prefs.remove(longPreferencesKey(it)) }
            prefs[longPreferencesKey(packageName)] = shownAt
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val SELECTED_PACKAGES = stringSetPreferencesKey("selected_packages")
        val PAUSE_SECONDS = intPreferencesKey("pause_seconds")
        val COOLDOWN_MINUTES = intPreferencesKey("cooldown_minutes")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

        fun Preferences.timestamps(): Map<String, Long> =
            asMap().mapNotNull { (key, value) -> (value as? Long)?.let { key.name to it } }.toMap()
    }
}
