package net.pipe01.pinepartner.utils

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class AppInfo(val fullInfo: ApplicationInfo, val label: String)

class AppInfoCache(private val pm: PackageManager) {
    private val cache = mutableMapOf<String, AppInfo?>()

    fun getAppInfo(packageName: String): AppInfo? {
        return cache.getOrPut(packageName) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                val label = pm.getApplicationLabel(appInfo).toString()
                AppInfo(appInfo, label)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}