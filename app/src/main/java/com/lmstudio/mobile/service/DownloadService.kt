package com.lmstudio.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
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

private const val TAG = "DownloadService"

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
            Log.i(TAG, "start requested for modelId=$modelId")
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate called")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        if (intent?.action == ACTION_START) {
            val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
            if (modelId != null) {
                Log.i(TAG, "Starting download for: $modelId")
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
        Log.d(TAG, "createNotificationChannel")
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created")
    }

    private suspend fun downloadModel(modelId: String) {
        Log.i(TAG, "downloadModel START: $modelId")
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading: $modelId")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)

        try {
            // Check if download was cancelled before starting
            if (downloadManager.isCancelled(modelId)) {
                Log.w(TAG, "downloadModel CANCELLED before start: $modelId")
                return
            }
            
            // Updated logic to find GGUF file or use the provided path
            val downloadUrl = if (modelId.contains(".gguf", ignoreCase = true)) {
                 "https://huggingface.co/$modelId"
            } else {
                 "https://huggingface.co/$modelId/resolve/main/${modelId.substringAfterLast("/")}.gguf"
            }
            
            Log.i(TAG, "downloadModel URL: $downloadUrl")

            val url = URL(downloadUrl)
            Log.d(TAG, "Opening connection to URL...")
            val connection = url.openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            Log.d(TAG, "Connecting...")
            connection.connect()
            val fileSize = connection.contentLength
            Log.i(TAG, "Connection established, fileSize=$fileSize bytes")
            val input = connection.getInputStream()

            // Use app's external files directory - safer than public storage
            val modelsDir = try {
                val dir = getExternalFilesDir(null)?.let { File(it, "models") }
                Log.d(TAG, "External files dir: ${dir?.absolutePath}")
                dir
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get external files dir, using cache: ${e.message}", e)
                // Fallback to app cache directory
                File(cacheDir, "models")
            }
            
            if (modelsDir == null) {
                Log.e(TAG, "modelsDir is null!")
                throw Exception("Unable to access storage directory")
            }
            
            Log.d(TAG, "Models directory: ${modelsDir.absolutePath}, exists=${modelsDir.exists()}")
            if (!modelsDir.exists()) {
                Log.i(TAG, "Creating models directory...")
                val created = modelsDir.mkdirs()
                if (!created) {
                    throw Exception("Failed to create models directory at ${modelsDir.absolutePath}")
                }
                Log.i(TAG, "Models directory created successfully")
            }
            
            val fileName = modelId.substringAfterLast("/").replace(".gguf", "") + ".gguf"
            val outputFile = File(modelsDir, fileName)
            Log.i(TAG, "Output file: ${outputFile.absolutePath}")
            
            // Create parent directories if they don't exist
            outputFile.parentFile?.mkdirs()
            
            Log.d(TAG, "Creating output stream...")
            val output = FileOutputStream(outputFile)
            Log.d(TAG, "Output stream created, starting download...")

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int
            var lastUpdate = 0L

            while (input.read(data).also { count = it } != -1) {
                // Check if download was cancelled
                if (downloadManager.isCancelled(modelId)) {
                    Log.w(TAG, "Download CANCELLED during transfer at $total bytes")
                    output.close()
                    input.close()
                    outputFile.delete() // Delete incomplete file
                    return
                }
                
                // Check if download is paused
                while (downloadManager.isPaused(modelId)) {
                    Log.d(TAG, "Download PAUSED, waiting...")
                    delay(100)
                }
                
                total += count
                val progress = if (fileSize > 0) (total * 100 / fileSize).toInt() else 0
                
                if (System.currentTimeMillis() - lastUpdate > 500) {
                    Log.v(TAG, "Progress: $progress% (${formatSize(total)}/${formatSize(fileSize.toLong())})")
                    notificationBuilder
                        .setProgress(100, progress, fileSize <= 0)
                        .setContentText("$progress% - ${formatSize(total)}/${formatSize(fileSize.toLong())}")
                    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                    downloadManager.updateProgress(modelId, progress)
                    lastUpdate = System.currentTimeMillis()
                }
                
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()
            Log.i(TAG, "Download completed: $total bytes")

            // Check again if cancelled before saving model
            if (downloadManager.isCancelled(modelId)) {
                Log.w(TAG, "Download CANCELLED before saving to database")
                outputFile.delete()
                return
            }

            Log.i(TAG, "Saving model to database: $fileName")
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
            Log.i(TAG, "Model saved to database successfully")
            
            downloadManager.setCompleted(modelId)
            Log.i(TAG, "downloadModel COMPLETE: $modelId")

            notificationBuilder
                .setContentText("Download complete ✓")
                .setProgress(0, 0, false)
                .setOngoing(false)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())

        } catch (e: Exception) {
            Log.e(TAG, "downloadModel FAILED: ${e.message}", e)
            downloadManager.setError(modelId, e.message ?: "Unknown error")
            notificationBuilder
                .setContentText("Download failed: ${e.message}")
                .setProgress(0, 0, false)
                .setOngoing(false)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        } finally {
            Log.d(TAG, "downloadModel finally: activeDownloads count=${downloadManager.activeDownloads.value.size}")
            if (downloadManager.activeDownloads.value.isEmpty()) {
                Log.i(TAG, "Stopping foreground service")
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
