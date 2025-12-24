package com.lmstudio.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lmstudio.mobile.data.repository.ModelRepository
import com.lmstudio.mobile.domain.model.LLMModel
import com.lmstudio.mobile.domain.model.ModelFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var modelRepository: ModelRepository

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
            val downloadUrl = "https://huggingface.co/$modelId/resolve/main/model.gguf"
            val url = URL(downloadUrl)
            val connection = url.openConnection()
            connection.connect()
            val fileSize = connection.contentLength
            val input = connection.getInputStream()

            // Path: /storage/emulated/0/LM studio Mobile/
            val publicDir = File(Environment.getExternalStorageDirectory(), "LM studio Mobile")
            if (!publicDir.exists()) {
                publicDir.mkdirs()
            }
            
            val fileName = modelId.substringAfterLast("/") + ".gguf"
            val outputFile = File(publicDir, fileName)
            val output = FileOutputStream(outputFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int
            var lastUpdate = 0L

            while (input.read(data).also { count = it } != -1) {
                total += count
                val progress = if (fileSize > 0) (total * 100 / fileSize).toInt() else 0
                
                if (System.currentTimeMillis() - lastUpdate > 500) {
                    notificationBuilder
                        .setProgress(100, progress, fileSize <= 0)
                        .setContentText("$progress%")
                    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                    downloadManager.updateProgress(modelId, progress)
                    lastUpdate = System.currentTimeMillis()
                }
                
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()

            // Register in database
            val newModel = LLMModel(
                id = modelId,
                name = fileName,
                path = outputFile.absolutePath,
                format = ModelFormat.GGUF,
                size = total,
                quantization = "Unknown",
                parameters = "Unknown",
                contextLength = 2048,
                addedAt = System.currentTimeMillis(),
                isLoaded = false,
                huggingFaceId = modelId
            )
            modelRepository.insertModel(newModel)
            
            downloadManager.setCompleted(modelId)

            notificationBuilder
                .setContentText("Download complete")
                .setProgress(0, 0, false)
                .setOngoing(false)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())

        } catch (e: Exception) {
            downloadManager.setError(modelId, e.message ?: "Unknown error")
            notificationBuilder
                .setContentText("Download failed: ${e.message}")
                .setProgress(0, 0, false)
                .setOngoing(false)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        } finally {
            // Check if any other downloads are running before stopping foreground
            if (downloadManager.activeDownloads.value.isEmpty()) {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
