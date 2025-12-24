package com.lmstudio.mobile.ui.screens.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmstudio.mobile.data.remote.api.HuggingFaceApi
import com.lmstudio.mobile.data.remote.dto.HFModelDto
import com.lmstudio.mobile.domain.model.DeviceCapabilities
import com.lmstudio.mobile.service.DownloadService
import com.lmstudio.mobile.util.DeviceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsState(
    val models: List<HFModelDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val deviceCapabilities: DeviceCapabilities? = null
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val huggingFaceApi: HuggingFaceApi,
    private val deviceUtils: DeviceUtils
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    init {
        loadDeviceCapabilities()
        loadPopularModels()
    }

    private fun loadDeviceCapabilities() {
        _state.value = _state.value.copy(
            deviceCapabilities = deviceUtils.getDeviceCapabilities()
        )
    }

    fun loadPopularModels() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
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
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val results = huggingFaceApi.searchModels(query = query)
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

    fun downloadModel(model: HFModelDto) {
        DownloadService.start(context, model.id)
    }
}
