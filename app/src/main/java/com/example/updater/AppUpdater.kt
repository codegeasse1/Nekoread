package com.example.updater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A newer Nekoread release found on GitHub. */
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val releaseUrl: String
)

private fun UpdateInfo.isNewerThanInstalled(context: Context): Boolean =
    AppUpdater.compareVersions(version, BuildConfig.VERSION_NAME) > 0

/**
 * Checks GitHub for new Nekoread releases and surfaces them to the user (notification + in-app
 * banner on the Settings screen). Since the app is sideloaded (no Play Store), this is the app's
 * own "update available" mechanism. The check is one small JSON call to the public GitHub API,
 * cached to at most one fetch per 6 hours so it never hammers the API or slows the app.
 */
object AppUpdater {

    const val CHANNEL_ID = "app_updates"
    const val NOTIFICATION_ID = 100
    const val EXTRA_SHOW_UPDATE = "nekoread_show_update"

    private const val KEY_ENABLED = "update_check_enabled"
    private const val KEY_LAST_CHECKED = "update_last_checked"
    private const val KEY_VERSION = "update_latest_version"
    private const val KEY_APK_URL = "update_apk_url"
    private const val KEY_RELEASE_URL = "update_release_url"
    private const val KEY_LAST_NOTIFIED = "update_last_notified_version"
    private const val MIN_CHECK_INTERVAL = 6L * 60L * 60L * 1000L // at most one fetch per 6 hours

    private const val RELEASE_API = "https://api.github.com/repos/codegeasse1/Nekoread/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun prefs(context: Context) =
        context.getSharedPreferences("nekoread_settings", Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** The most recently fetched release, but only when it is newer than the installed build. */
    fun currentUpdate(context: Context): UpdateInfo? =
        readCached(context)?.takeIf { it.isNewerThanInstalled(context) }

    private fun readCached(context: Context): UpdateInfo? {
        val p = prefs(context)
        val version = p.getString(KEY_VERSION, null) ?: return null
        val apkUrl = p.getString(KEY_APK_URL, null) ?: return null
        val releaseUrl = p.getString(KEY_RELEASE_URL, null) ?: ""
        return UpdateInfo(version, apkUrl, releaseUrl)
    }

    /**
     * Background check for a new release. Fetches GitHub's latest release (cached: at most one
     * network call per [MIN_CHECK_INTERVAL] unless [force]). Persists whatever was found and
     * returns the update if it is newer than the installed version, otherwise null.
     */
    suspend fun runCheck(context: Context, force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ENABLED, true)) return@withContext null
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong(KEY_LAST_CHECKED, 0L) < MIN_CHECK_INTERVAL) {
            return@withContext currentUpdate(context)
        }
        val info = try {
            fetchLatestRelease()
        } catch (e: Exception) {
            null
        }
        if (info != null) {
            p.edit()
                .putString(KEY_VERSION, info.version)
                .putString(KEY_APK_URL, info.apkUrl)
                .putString(KEY_RELEASE_URL, info.releaseUrl)
                .putLong(KEY_LAST_CHECKED, now)
                .apply()
            return@withContext info.takeIf { it.isNewerThanInstalled(context) }
        }
        null
    }

    private fun fetchLatestRelease(): UpdateInfo {
        val request = Request.Builder()
            .url(RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(body)
            val version = json.optString("tag_name").trim().removePrefix("v").ifBlank { BuildConfig.VERSION_NAME }
            val releaseUrl = json.optString("html_url")
            var apkUrl = ""
            json.optJSONArray("assets")?.let { assets ->
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            if (apkUrl.isBlank()) throw IOException("no apk asset in release")
            return UpdateInfo(version, apkUrl, releaseUrl)
        }
    }

    /** Post the system "update available" notification (tapping it opens the in-app update dialog). */
    fun showUpdateNotification(context: Context, info: UpdateInfo) {
        ensureChannel(context)
        val p = prefs(context)
        if (p.getString(KEY_LAST_NOTIFIED, null) == info.version) return // don't re-notify every launch
        p.edit().putString(KEY_LAST_NOTIFIED, info.version).apply()

        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SHOW_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Nekoread update available")
            .setContentText("v${info.version} is ready — tap to update")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — the in-app banner still shows the update.
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications about new Nekoread releases" }
            nm.createNotificationChannel(channel)
        }
    }

    /** True if [context] can already POST_NOTIFICATIONS (or is on an API below 33). */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Open the release page for this update in the browser. */
    fun viewRelease(context: Context, info: UpdateInfo) {
        if (info.releaseUrl.isNotBlank()) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)))
            } catch (_: Exception) {
            }
        }
    }

    /** True if the device can install apps from this app (needed for the in-app updater). */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Open the system screen that lets the user allow installing apps from this app. */
    fun openInstallPermissionScreen(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    /** Compare dotted version strings ("1.1" vs "1.1.0" are equal; "1.10" > "1.9"). */
    fun compareVersions(a: String, b: String): Int {
        fun parts(s: String): List<Int> =
            s.trim().removePrefix("v").split('.').map { it.trim().toIntOrNull() ?: 0 }
        val pa = parts(a)
        val pb = parts(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
