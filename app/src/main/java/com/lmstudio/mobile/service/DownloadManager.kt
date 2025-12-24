package com.lmstudio.mobile.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val modelId: String,
    val progress: Int,
    val isCompleted: Boolean = false,
    val error: String? = null
)

@Singleton
class DownloadManager @Inject constructor() {
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    fun updateProgress(modelId: String, progress: Int) {
        val current = _activeDownloads.value.toMutableMap()
        current[modelId] = DownloadProgress(modelId, progress)
        _activeDownloads.value = current
    }

    fun setCompleted(modelId: String) {
        val current = _activeDownloads.value.toMutableMap()
        current.remove(modelId)
        _activeDownloads.value = current
    }

    fun setError(modelId: String, error: String) {
        val current = _activeDownloads.value.toMutableMap()
        current[modelId] = DownloadProgress(modelId, 0, error = error)
        _activeDownloads.value = current
    }
}
