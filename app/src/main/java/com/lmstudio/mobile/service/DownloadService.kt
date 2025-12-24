package com.lmstudio.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class DownloadService : Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_START = "ACTION_START"
        const val EXTRA_MODEL_ID = "EXTRA_MODEL_ID"
        private const val NOTIFICATION_CHANNEL_ID = "DOWNLOAD_CHANNEL"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context, modelId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
            if (modelId != null) {
                createNotificationChannel()
                val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Downloading Model")
                    .setContentText("Starting download...")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .build()
                startForeground(NOTIFICATION_ID, notification)

                coroutineScope.launch {
                    downloadModel(modelId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private suspend fun downloadModel(modelId: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading: $modelId")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)

        try {
            // Placeholder: Replace with actual logic to get download URL
            val downloadUrl = "https://huggingface.co/$modelId/resolve/main/model.gguf"

            val url = URL(downloadUrl)
            val connection = url.openConnection()
            connection.connect()
            val fileSize = connection.contentLength
            val input = connection.getInputStream()

            val outputFile = File(getExternalFilesDir(null), "$modelId.gguf")
            val output = FileOutputStream(outputFile)

            val data = ByteArray(1024)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count
                val progress = (total * 100 / fileSize).toInt()
                notificationBuilder
                    .setProgress(100, progress, false)
                    .setContentText("$progress%")
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()

            notificationBuilder
                .setContentText("Download complete")
                .setProgress(0, 0, false)
                .setOngoing(false)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())

        } catch (e: Exception) {
            notificationBuilder
                .setContentText("Download failed: ${e.message}")
                .setProgress(0, 0, false)
                .setOngoing(false)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        } finally {
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
