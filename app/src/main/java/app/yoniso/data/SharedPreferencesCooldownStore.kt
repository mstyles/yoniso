package app.yoniso.data

import android.content.Context
import androidx.core.content.edit
import app.yoniso.core.CooldownStore

/**
 * Stores when each app's pause was last shown, keyed by package name.
 * Interim storage for Phase 2; Phase 3 moves this to DataStore.
 */
internal class SharedPreferencesCooldownStore(context: Context) : CooldownStore {
    private val prefs = context.getSharedPreferences("cooldowns", Context.MODE_PRIVATE)

    override fun load(): Map<String, Long> =
        prefs.all.mapNotNull { (pkg, value) -> (value as? Long)?.let { pkg to it } }.toMap()

    override fun save(packageName: String, shownAt: Long) {
        prefs.edit { putLong(packageName, shownAt) }
    }
}
