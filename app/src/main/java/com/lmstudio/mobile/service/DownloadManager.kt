package com.lmstudio.mobile.service

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DownloadManager"

data class DownloadProgress(
    val modelId: String,
    val progress: Int,
    val isCompleted: Boolean = false,
    val isPaused: Boolean = false,
    val error: String? = null
)

@Singleton
class DownloadManager @Inject constructor() {
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()
    
    private val _cancelledDownloads = MutableStateFlow<Set<String>>(emptySet())
    val cancelledDownloads: StateFlow<Set<String>> = _cancelledDownloads.asStateFlow()
    
    private val _pausedDownloads = MutableStateFlow<Set<String>>(emptySet())
    val pausedDownloads: StateFlow<Set<String>> = _pausedDownloads.asStateFlow()

    init {
        Log.d(TAG, "DownloadManager initialized")
    }

    fun updateProgress(modelId: String, progress: Int) {
        val current = _activeDownloads.value.toMutableMap()
        val isPaused = _pausedDownloads.value.contains(modelId)
        Log.v(TAG, "updateProgress: $modelId=$progress%, paused=$isPaused")
        current[modelId] = DownloadProgress(modelId, progress, isPaused = isPaused)
        _activeDownloads.value = current
    }

    fun setCompleted(modelId: String) {
        Log.i(TAG, "setCompleted: $modelId")
        val current = _activeDownloads.value.toMutableMap()
        current.remove(modelId)
        _activeDownloads.value = current
        _cancelledDownloads.value = _cancelledDownloads.value - modelId
        _pausedDownloads.value = _pausedDownloads.value - modelId
    }

    fun setError(modelId: String, error: String) {
        Log.e(TAG, "setError: $modelId - $error")
        val current = _activeDownloads.value.toMutableMap()
        current[modelId] = DownloadProgress(modelId, 0, error = error)
        _activeDownloads.value = current
        _cancelledDownloads.value = _cancelledDownloads.value - modelId
    }
    
    fun cancelDownload(modelId: String) {
        Log.i(TAG, "cancelDownload: $modelId")
        _cancelledDownloads.value = _cancelledDownloads.value + modelId
        _pausedDownloads.value = _pausedDownloads.value - modelId
        val current = _activeDownloads.value.toMutableMap()
        current.remove(modelId)
        _activeDownloads.value = current
    }
    
    fun pauseDownload(modelId: String) {
        Log.i(TAG, "pauseDownload: $modelId")
        _pausedDownloads.value = _pausedDownloads.value + modelId
    }
    
    fun resumeDownload(modelId: String) {
        Log.i(TAG, "resumeDownload: $modelId")
        _pausedDownloads.value = _pausedDownloads.value - modelId
    }
    
    fun isCancelled(modelId: String): Boolean {
        val cancelled = _cancelledDownloads.value.contains(modelId)
        if (cancelled) Log.d(TAG, "isCancelled: $modelId=true")
        return cancelled
    }
    
    fun isPaused(modelId: String): Boolean {
        val paused = _pausedDownloads.value.contains(modelId)
        if (paused) Log.d(TAG, "isPaused: $modelId=true")
        return paused
    }
}
