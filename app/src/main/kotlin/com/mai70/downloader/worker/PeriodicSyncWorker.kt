package com.mai70.downloader.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mai70.downloader.data.PreferencesManager
import com.mai70.downloader.engine.Mai70DownloaderEngine
import com.mai70.downloader.engine.SyncResult
import java.io.File
import java.util.concurrent.TimeUnit

class PeriodicSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)

        if (!prefs.periodicCheckEnabled) {
            return Result.success()
        }

        val downloadDir = if (prefs.downloadDirPath.isNotEmpty()) {
            File(prefs.downloadDirPath)
        } else {
            File(applicationContext.getExternalFilesDir(null), "70mai_downloads")
        }

        val engine = Mai70DownloaderEngine(
            context = applicationContext,
            host = prefs.host,
            port = prefs.port
        )

        showNotification("70mai Downloader", "Checking dashcam for new files...")

        val result = engine.syncFiles(
            outputDir = downloadDir,
            deleteAfterDownload = prefs.autoDeleteAfterDownload
        )

        return when (result) {
            is SyncResult.Success -> {
                prefs.lastSyncTime = System.currentTimeMillis()
                val msg = "Sync completed: ${result.downloadedFiles.size} downloaded, ${result.deletedFiles.size} deleted."
                showNotification("70mai Downloader Sync Complete", msg)
                Result.success()
            }
            is SyncResult.Error -> {
                showNotification("70mai Downloader Sync Failed", "Sync failed: ${result.message}")
                Result.retry()
            }
        }
    }

    private fun showNotification(title: String, content: String) {
        val channelId = "70mai_sync_channel"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "70mai Periodic Downloader",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "70mai_periodic_sync_work"
        private const val NOTIFICATION_ID = 7001

        fun schedulePeriodicSync(context: Context, intervalMinutes: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
                intervalMinutes.coerceAtLeast(15L),
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
