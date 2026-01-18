package com.pcexplorer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pcexplorer.app.MainActivity
import com.pcexplorer.app.R
import com.pcexplorer.core.common.Logger
import com.pcexplorer.core.domain.model.TransferTask
import com.pcexplorer.core.domain.usecase.TransferFileUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "FileTransferService"
private const val CHANNEL_ID = "file_transfer_channel"
private const val NOTIFICATION_ID = 1

@AndroidEntryPoint
class FileTransferService : Service() {

    @Inject
    lateinit var transferFileUseCase: TransferFileUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Logger.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d(TAG, "Service started")

        if (!isRunning) {
            isRunning = true
            startForeground()
            observeTransfers()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isRunning = false
        Logger.d(TAG, "Service destroyed")
    }

    private fun startForeground() {
        val notification = createNotification("Preparing transfer...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeTransfers() {
        serviceScope.launch {
            transferFileUseCase.activeTransfers.collectLatest { transfers ->
                if (transfers.isEmpty()) {
                    // No active transfers, stop service
                    stopSelf()
                } else {
                    // Update notification with current transfer info
                    val transfer = transfers.first()
                    updateNotification(transfer)
                }
            }
        }
    }

    private fun updateNotification(transfer: TransferTask) {
        val notification = createNotification(
            text = "${transfer.fileName} - ${transfer.progressPercent}%",
            progress = transfer.progressPercent
        )

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "File Transfers",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of file transfers"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String, progress: Int = 0): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transferring files")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (progress > 0) {
                    setProgress(100, progress, false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()
    }
}
