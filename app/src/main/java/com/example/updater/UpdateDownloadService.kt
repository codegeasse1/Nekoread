package com.example.updater

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads a Nekoread update in the background (as a foreground service with a progress
 * notification) and then hands the APK to Android's standard install flow via FileProvider.
 * Because every APK is signed with the same real key, the update installs over the old version.
 */
class UpdateDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val version = intent.getStringExtra(EXTRA_VERSION) ?: ""
        AppUpdater.ensureChannel(this)
        startForeground(NOTIF_ID, buildNotification("Downloading update v$version...", 0, indeterminate = true))

        if (activeJob?.isActive == true) activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val dest = download(url)
                installApk(dest)
            } catch (e: Exception) {
                notifyFailure(e.message ?: "Download failed")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun download(url: String): File {
        val dest = File(filesDir, "updates/nekoread-update.apk")
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val total = body.contentLength()
            dest.parentFile?.mkdirs()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                            notifyProgress(percent)
                        }
                    }
                }
            }
        }
        return dest
    }

    private fun notifyProgress(percent: Int) {
        val notification = buildNotification("Downloading update... $percent%", percent, indeterminate = false)
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun notifyFailure(message: String) {
        val notification = NotificationCompat.Builder(this, AppUpdater.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Update download failed")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun buildNotification(text: String, percent: Int, indeterminate: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AppUpdater.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Nekoread update")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate)
            .build()
    }

    private fun installApk(dest: File) {
        if (!AppUpdater.canInstallPackages(this)) {
            AppUpdater.openInstallPermissionScreen(this)
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", dest)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1
        private const val EXTRA_URL = "update_url"
        private const val EXTRA_VERSION = "update_version"

        fun start(context: Context, info: UpdateInfo) {
            val intent = Intent(context, UpdateDownloadService::class.java)
                .putExtra(EXTRA_URL, info.apkUrl)
                .putExtra(EXTRA_VERSION, info.version)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
