package app.yoniso.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager

/** A launchable app the user can choose to pause. */
data class AppEntry(val packageName: String, val label: String)

/**
 * Finds installed apps through their launcher activities. The manifest declares a
 * `<queries>` entry for each intent resolved here; `QUERY_ALL_PACKAGES` is not used.
 *
 * PackageManager calls can be slow, so call these off the main thread.
 */
class InstalledApps(context: Context) {
    private val context = context.applicationContext
    private val pm = context.packageManager

    /** Launchable apps the user may select, sorted by name. Never includes [neverPausable] apps. */
    fun selectableApps(): List<AppEntry> {
        val excluded = neverPausable()
        return query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filterNot { it.packageName in excluded }
            .map { AppEntry(it.packageName, it.loadLabel(pm).toString()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /**
     * Apps that must never be paused, whatever is stored: Yoniso itself, every launcher
     * (so the home screen is never blocked), the default dialer, and Android Settings
     * (so the user can always reach the switch that turns Yoniso off).
     */
    fun neverPausable(): Set<String> = buildSet {
        add(context.packageName)
        add(SETTINGS_PACKAGE)
        query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)).forEach { add(it.activityInfo.packageName) }
        query(Intent(Settings.ACTION_SETTINGS)).forEach { add(it.activityInfo.packageName) }
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(::add)
    }

    fun loadIcon(packageName: String): Drawable? = try {
        pm.getApplicationIcon(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun query(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private companion object {
        /** AOSP Settings, in case resolving the Settings intent finds nothing. */
        const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
