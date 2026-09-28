package com.example.manager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemEssential: Boolean,
    val systemCategory: String? = null
)

class InstalledAppsManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun getInstalledLaunchableApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        val homePackageName = getHomePackageName()
        val dialerPackageName = getDialerPackageName()

        val apps = mutableListOf<InstalledAppItem>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val packageName = resolveInfo.activityInfo?.packageName ?: continue
            if (seenPackages.contains(packageName)) continue
            seenPackages.add(packageName)

            val appName = resolveInfo.loadLabel(packageManager)?.toString() ?: packageName
            val icon = resolveInfo.loadIcon(packageManager)

            val isEssential = isEssentialApp(packageName, homePackageName, dialerPackageName)
            val category = when {
                packageName == context.packageName -> "This App"
                packageName == homePackageName -> "Home Launcher"
                packageName == dialerPackageName || isDialerOrEmergency(packageName) -> "Phone / Emergency"
                isSettingsApp(packageName) -> "System Settings"
                isEssential -> "System Essential"
                else -> null
            }

            apps.add(
                InstalledAppItem(
                    packageName = packageName,
                    appName = appName,
                    icon = icon,
                    isSystemEssential = isEssential,
                    systemCategory = category
                )
            )
        }

        apps.sortedWith(
            compareBy<InstalledAppItem> { it.isSystemEssential }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun isEssentialApp(
        packageName: String,
        homePackage: String?,
        dialerPackage: String?
    ): Boolean {
        if (packageName == context.packageName) return true
        if (packageName == homePackage) return true
        if (packageName == dialerPackage) return true
        if (isSettingsApp(packageName)) return true
        if (isDialerOrEmergency(packageName)) return true
        if (packageName.startsWith("com.android.systemui")) return true
        if (packageName.startsWith("com.google.android.packageinstaller") ||
            packageName.startsWith("com.android.packageinstaller")
        ) return true
        return false
    }

    private fun isSettingsApp(packageName: String): Boolean {
        return packageName.contains("settings", ignoreCase = true) ||
                packageName == "com.android.settings" ||
                packageName == "com.google.android.settings"
    }

    private fun isDialerOrEmergency(packageName: String): Boolean {
        return packageName.contains("dialer", ignoreCase = true) ||
                packageName.contains("telecom", ignoreCase = true) ||
                packageName.contains("phone", ignoreCase = true) ||
                packageName.contains("emergency", ignoreCase = true)
    }

    private fun getHomePackageName(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    private fun getDialerPackageName(): String? {
        val intent = Intent(Intent.ACTION_DIAL)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }
}
