package eu.kanade.tachiyomi

import android.content.Context
import android.content.pm.PackageManager

/**
 * Host-app info that loaded extensions read for User-Agent strings (Tadami/Mihon-compatible).
 */
object AppInfo {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getVersionCode(): Int {
        val ctx = appContext ?: return 1
        return try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            1
        }
    }

    fun getVersionName(): String {
        val ctx = appContext ?: return "1.0"
        return try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }
}
