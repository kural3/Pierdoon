package com.ku.pierdoon.remote

import com.ku.pierdoon.data.InterfaceLanguage
import android.content.Context
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ku.pierdoon.MainActivity
import com.ku.pierdoon.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while [PcTransfers] moves tracks, with a notification of how far it got.
 * Once the queue is empty it leaves one line behind: how many arrived, and how many didn't.
 */
class TransferService : Service() {
    // The notification in the chosen language (Android 12 and older).
    override fun attachBaseContext(base: Context) = super.attachBaseContext(InterfaceLanguage.wrap(base))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PcTransfers.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.transfer_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            progress(PcTransfers.tasks.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (watcher == null) {
            watcher = scope.launch {
                PcTransfers.tasks.collect { tasks ->
                    if (tasks.any { it.active }) {
                        if (canNotify()) manager.notify(NOTIFICATION_ID, progress(tasks))
                    } else {
                        ServiceCompat.stopForeground(this@TransferService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        if (tasks.isNotEmpty() && canNotify()) manager.notify(SUMMARY_ID, summary(tasks))
                        stopSelf()
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

    /** Android 13+ drops notifications without the permission; updates are skipped then. */
    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun progress(tasks: List<TransferTask>): android.app.Notification {
        val current = tasks.firstOrNull { it.status is TransferStatus.Running } ?: tasks.firstOrNull { it.active }
        val left = tasks.count { it.active }
        val toPhone = current?.direction == TransferDirection.ToPhone
        return builder()
            .setContentTitle(getString(if (toPhone) R.string.transfer_to_phone_title else R.string.transfer_to_pc_title))
            .setContentText(
                listOfNotNull(current?.title, resources.getQuantityString(R.plurals.transfer_left, left, left)).joinToString(" · ")
            )
            .apply {
                when (val s = current?.status) {
                    is TransferStatus.Running ->
                        if (s.progress != null) setProgress(100, (s.progress * 100).toInt(), false) else setProgress(0, 0, true)
                    else -> setProgress(0, 0, true)
                }
            }
            .setOngoing(true)
            .build()
    }

    private fun summary(tasks: List<TransferTask>): android.app.Notification {
        val done = tasks.count { it.status == TransferStatus.Done }
        val failed = tasks.filter { it.status is TransferStatus.Failed }
        val toPhone = tasks.all { it.direction == TransferDirection.ToPhone }
        val reason = failed.firstNotNullOfOrNull { (it.status as? TransferStatus.Failed)?.reason }
        return builder()
            .setContentTitle(
                resources.getQuantityString(
                    if (toPhone) R.plurals.transfer_done_phone else R.plurals.transfer_done_pc, done, done,
                )
            )
            .apply {
                if (failed.isNotEmpty()) {
                    setContentText(
                        resources.getQuantityString(R.plurals.transfer_failed, failed.size, failed.size) + ": " +
                            getString(
                                when (reason) {
                                    TransferFailure.Offline -> R.string.transfer_reason_offline
                                    TransferFailure.NoSpace -> R.string.transfer_reason_space
                                    TransferFailure.Missing -> R.string.transfer_reason_missing
                                    else -> R.string.transfer_reason_other
                                }
                            )
                    )
                }
            }
            .setAutoCancel(true)
            .build()
    }

    private fun builder() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .setOnlyAlertOnce(true)
        .setSilent(true)

    companion object {
        private const val CHANNEL = "transfers"
        private const val NOTIFICATION_ID = 3
        private const val SUMMARY_ID = 4
    }
}
