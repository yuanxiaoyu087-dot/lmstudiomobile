package com.lmstudio.mobile.ui.screens.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmstudio.mobile.llm.engine.LLMEngine
import com.lmstudio.mobile.llm.monitoring.ResourceMetrics
import com.lmstudio.mobile.util.DeviceUtils
import com.lmstudio.mobile.util.ResourceMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetricsState(
    val metrics: ResourceMetrics = ResourceMetrics(0f, 0f, 0f, 0f),
    val systemMetrics: com.lmstudio.mobile.domain.model.SystemMetrics? = null,
    val modelInfo: ModelInfo? = null,
    val deviceInfo: String = "Unknown"
)

data class ModelInfo(
    val name: String,
    val parameters: String?,
    val contextLength: Int
)

@HiltViewModel
class MetricsViewModel @Inject constructor(
    private val llmEngine: LLMEngine,
    private val deviceUtils: DeviceUtils,
    private val resourceMonitor: ResourceMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(MetricsState())
    val state: StateFlow<MetricsState> = _state.asStateFlow()

    private var monitoringJob: Job? = null

    init {
        loadDeviceInfo()
        loadModelInfo()
    }

    private fun loadDeviceInfo() {
        val capabilities = deviceUtils.getDeviceCapabilities()
        val gpuInfo = if (capabilities.gpuName != null) {
            "GPU: ${capabilities.gpuName}"
        } else {
            "GPU: Unknown"
        }
        _state.value = _state.value.copy(
            deviceInfo = "CPU Cores: ${capabilities.cpuCores}, RAM: ${capabilities.totalRam / (1024 * 1024 * 1024)}GB, $gpuInfo"
        )
    }

    private fun loadModelInfo() {
        val modelInfo = llmEngine.getModelInfo()
        if (modelInfo != null) {
            _state.value = _state.value.copy(
                modelInfo = ModelInfo(
                    name = modelInfo.name,
                    parameters = modelInfo.parameters,
                    contextLength = modelInfo.contextLength
                )
            )
        }
    }

    fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            while (true) {
                val engineMetrics = llmEngine.getResourceUsage()
                val systemMetrics = resourceMonitor.getCurrentMetrics()
                
                _state.value = _state.value.copy(
                    metrics = engineMetrics,
                    systemMetrics = systemMetrics
                )
                delay(1000) // Update every second
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
}
