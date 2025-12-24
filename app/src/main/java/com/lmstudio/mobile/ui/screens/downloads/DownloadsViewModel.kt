package com.lmstudio.mobile.ui.screens.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmstudio.mobile.data.remote.api.HuggingFaceApi
import com.lmstudio.mobile.data.remote.dto.HFModelDto
import com.lmstudio.mobile.data.remote.dto.ModelFile
import com.lmstudio.mobile.domain.model.DeviceCapabilities
import com.lmstudio.mobile.service.DownloadManager
import com.lmstudio.mobile.service.DownloadProgress
import com.lmstudio.mobile.service.DownloadService
import com.lmstudio.mobile.util.DeviceUtils
import com.lmstudio.mobile.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsState(
    val models: List<HFModelDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val deviceCapabilities: DeviceCapabilities? = null,
    val isNetworkAvailable: Boolean = true,
    val activeDownloads: Map<String, DownloadProgress> = emptyMap(),
    val selectedModelFiles: List<ModelFile>? = null,
    val selectedModelId: String? = null
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val huggingFaceApi: HuggingFaceApi,
    private val deviceUtils: DeviceUtils,
    private val networkUtils: NetworkUtils,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    init {
        checkNetwork()
        loadDeviceCapabilities()
        if (_state.value.isNetworkAvailable) {
            loadPopularModels()
        }
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadManager.activeDownloads.collectLatest { downloads ->
                _state.value = _state.value.copy(activeDownloads = downloads)
            }
        }
    }

    fun checkNetwork() {
        _state.value = _state.value.copy(isNetworkAvailable = networkUtils.isNetworkAvailable())
    }

    private fun loadDeviceCapabilities() {
        _state.value = _state.value.copy(
            deviceCapabilities = deviceUtils.getDeviceCapabilities()
        )
    }

    fun loadPopularModels() {
        if (!networkUtils.isNetworkAvailable()) {
            _state.value = _state.value.copy(isNetworkAvailable = false, isLoading = false)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, isNetworkAvailable = true)
            try {
                val results = huggingFaceApi.searchModels(
                    query = "gguf",
                    filter = "text-generation",
                    sort = "downloads",
                    limit = 50
                )
                _state.value = _state.value.copy(
                    models = results,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load models"
                )
            }
        }
    }

    fun searchModels(query: String) {
        if (!networkUtils.isNetworkAvailable()) {
            _state.value = _state.value.copy(isNetworkAvailable = false)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, isNetworkAvailable = true)
            try {
                val searchQuery = if (query.contains("gguf", ignoreCase = true)) query else "$query gguf"
                val results = huggingFaceApi.searchModels(query = searchQuery)
                _state.value = _state.value.copy(
                    models = results,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun selectModel(model: HFModelDto) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val details = huggingFaceApi.getModelDetails(model.id)
                val ggufFiles = details.siblings
                    .filter { it.rfilename.endsWith(".gguf", ignoreCase = true) }
                    .sortedBy { it.size ?: Long.MAX_VALUE }
                
                _state.value = _state.value.copy(
                    selectedModelFiles = ggufFiles,
                    selectedModelId = model.id,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to fetch files: ${e.message}")
            }
        }
    }

    fun dismissFileSelection() {
        _state.value = _state.value.copy(selectedModelFiles = null, selectedModelId = null)
    }

    fun downloadFile(modelId: String, fileName: String) {
        DownloadService.start(context, "$modelId/resolve/main/$fileName")
        dismissFileSelection()
    }
}
