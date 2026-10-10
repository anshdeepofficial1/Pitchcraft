package com.inferno.gallery.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.inferno.gallery.MainActivity
import com.inferno.gallery.data.network.UpdateChecker
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val UNIQUE_WORK = "PitchcraftUpdateCheck"
        private const val CHANNEL_ID = "pitchcraft_updates"
        private const val PREFS = "pitchcraft_update_prefs"
        private const val LAST_NOTIFIED_VERSION = "last_notified_version"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                24,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val currentVersion = runCatching {
            applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)
                .versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")

        val info = UpdateChecker.checkForUpdates(currentVersion)
            ?: return Result.retry()

        if (!info.isAvailable) return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_NOTIFIED_VERSION, null) == info.normalizedVersion) {
            return Result.success()
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "New Pitchcraft versions available from GitHub Releases"
            }
        )

        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("open_update_prompt", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            501,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sizeText = if (info.apkSizeBytes > 0) {
            " • ${formatBytes(info.apkSizeBytes)}"
        } else ""

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Pitchcraft ${info.version} is available")
            .setContentText("Tap to view and install the update${sizeText}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    info.changelog.take(500).ifBlank {
                        "A new Pitchcraft update is available on GitHub."
                    }
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(501, notification)
        prefs.edit().putString(LAST_NOTIFIED_VERSION, info.normalizedVersion).apply()
        return Result.success()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 100) "${mb.toInt()} MB" else String.format("%.1f MB", mb)
    }
}
