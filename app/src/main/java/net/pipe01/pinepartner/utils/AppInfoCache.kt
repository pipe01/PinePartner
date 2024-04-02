package net.pipe01.pinepartner.utils

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(val fullInfo: ApplicationInfo, val label: String)

object AppInfoCache {
    private val cache = mutableMapOf<String, AppInfo?>()
    private val iconCache = mutableMapOf<String, Drawable?>()

    fun getAppInfo(pm: PackageManager, packageName: String): AppInfo? {
        return cache.getOrPut(packageName) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)

                AppInfo(
                    fullInfo = appInfo,
                    label = pm.getApplicationLabel(appInfo).toString(),
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    
    fun getAppIcon(pm: PackageManager, packageName: String): Drawable? {
        return iconCache.getOrPut(packageName) {
            try {
                pm.getApplicationIcon(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}