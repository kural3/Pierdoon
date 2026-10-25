package com.ku.pierdoon.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ku.pierdoon.MainActivity
import com.ku.pierdoon.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while [Downloader] has work, with a notification showing what's going on.
 * Stops itself once the queue is empty; finished downloads need no notification, they're in the library.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Downloader.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.downloads), NotificationManager.IMPORTANCE_LOW)
            )
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(Downloader.tasks.value.filter { it.active }),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (watcher == null) {
            watcher = scope.launch {
                Downloader.tasks.collect { tasks ->
                    val active = tasks.filter { it.active }
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        manager.notify(NOTIFICATION_ID, notification(active))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(active: List<DownloadTask>) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(active.firstOrNull()?.title ?: getString(R.string.download_preparing))
            .setContentText(
                if (active.size > 1) getString(R.string.download_more, active.size - 1)
                else active.firstOrNull()?.artist
            )
            .apply {
                when (val s = active.firstOrNull()?.status) {
                    is DownloadStatus.Downloading ->
                        if (s.progress != null) setProgress(100, (s.progress * 100).toInt(), false) else setProgress(0, 0, true)
                    else -> setProgress(0, 0, true)
                }
            }
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_DOWNLOADS),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    companion object {
        const val ACTION_OPEN_DOWNLOADS = "com.ku.pierdoon.OPEN_DOWNLOADS"
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 2
    }
}
