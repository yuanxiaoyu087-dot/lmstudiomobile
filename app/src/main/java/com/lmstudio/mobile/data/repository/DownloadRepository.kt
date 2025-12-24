package com.lmstudio.mobile.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class DownloadRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    fun downloadModel(
        modelId: String,
        downloadUrl: String,
        destinationFile: File,
        onProgress: (Long, Long) -> Unit
    ): Flow<Result<File>> = flow {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(Result.failure(Exception("Download failed: ${response.code}")))
                return@flow
            }

            val body = response.body ?: run {
                emit(Result.failure(Exception("Response body is null")))
                return@flow
            }

            val contentLength = body.contentLength()
            var downloadedBytes = 0L

            destinationFile.parentFile?.mkdirs()
            FileOutputStream(destinationFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        onProgress(downloadedBytes, contentLength)
                    }
                }
            }

            emit(Result.success(destinationFile))
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error downloading model", e)
            emit(Result.failure(e))
        }
    }
}

