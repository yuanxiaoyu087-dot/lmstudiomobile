package com.lmstudio.mobile.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmstudio.mobile.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val nThreads: Int = 4,
    val draftNThreads: Int = 4,
    val recommendedThreads: Int = 4,
    
    val nGpuLayers: Int = 0,
    val draftNGpuLayers: Int = 0,
    val recommendedGpuLayers: Int = 0,
    
    val contextSize: Int = 2048,
    val draftContextSize: Int = 2048,
    
    val isDarkTheme: Boolean = false,
    val autoSaveChats: Boolean = true,
    
    val hasUnsavedChanges: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val deviceUtils: com.lmstudio.mobile.util.DeviceUtils
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val caps = deviceUtils.getDeviceCapabilities()
        viewModelScope.launch {
            // First run logic: if settings never set, use recommended
            if (!preferences.hasInferenceSettingsSet()) {
                preferences.setNThreads(caps.recommendedThreads)
                preferences.setNGpuLayers(if (caps.hasVulkan) 32 else 0)
                // Keep context size as 2048 default or similar
            }
            
            val threads = preferences.getNThreads()
            val gpuLayers = preferences.getNGpuLayers()
            val contextSize = preferences.getContextSize()
            
            _state.value = SettingsState(
                nThreads = threads,
                draftNThreads = threads,
                recommendedThreads = caps.recommendedThreads,
                nGpuLayers = gpuLayers,
                draftNGpuLayers = gpuLayers,
                recommendedGpuLayers = if (caps.hasVulkan) 80 else 0, // Using 80 as a safe upper mock
                contextSize = contextSize,
                draftContextSize = contextSize,
                isDarkTheme = preferences.isDarkTheme(),
                autoSaveChats = preferences.isAutoSaveChats()
            )
        }
    }

    fun setNThreads(threads: Int) {
        _state.value = _state.value.copy(
            draftNThreads = threads,
            hasUnsavedChanges = true
        )
    }

    fun setNGpuLayers(layers: Int) {
        _state.value = _state.value.copy(
            draftNGpuLayers = layers,
            hasUnsavedChanges = true
        )
    }

    fun setContextSize(size: Int) {
        _state.value = _state.value.copy(
            draftContextSize = size,
            hasUnsavedChanges = true
        )
    }

    fun applyInferenceSettings() {
        val currentState = _state.value
        viewModelScope.launch {
            preferences.setNThreads(currentState.draftNThreads)
            preferences.setNGpuLayers(currentState.draftNGpuLayers)
            preferences.setContextSize(currentState.draftContextSize)
            
            _state.value = currentState.copy(
                nThreads = currentState.draftNThreads,
                nGpuLayers = currentState.draftNGpuLayers,
                contextSize = currentState.draftContextSize,
                hasUnsavedChanges = false
            )
        }
    }

    fun discardInferenceSettings() {
        val currentState = _state.value
        _state.value = currentState.copy(
            draftNThreads = currentState.nThreads,
            draftNGpuLayers = currentState.nGpuLayers,
            draftContextSize = currentState.contextSize,
            hasUnsavedChanges = false
        )
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDarkTheme(enabled)
            _state.value = _state.value.copy(isDarkTheme = enabled)
        }
    }

    fun setAutoSaveChats(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoSaveChats(enabled)
            _state.value = _state.value.copy(autoSaveChats = enabled)
        }
    }
}
