package com.lmstudio.mobile.data.local.storage

import android.content.Context
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelStorageManager @Inject constructor(
    private val context: Context
) {
    private val modelsDirectory: File
        get() = File(context.getExternalFilesDir(null), "models").apply {
            if (!exists()) mkdirs()
        }

    fun getModelFile(fileName: String): File {
        return File(modelsDirectory, fileName)
    }

    fun getAllModelFiles(): List<File> {
        return modelsDirectory.listFiles()?.filter { it.isFile } ?: emptyList()
    }

    fun deleteModelFile(fileName: String): Boolean {
        return getModelFile(fileName).delete()
    }

    fun getModelsDirectoryPath(): String {
        return modelsDirectory.absolutePath
    }

    fun getAvailableStorageSpace(): Long {
        return modelsDirectory.usableSpace
    }
}

