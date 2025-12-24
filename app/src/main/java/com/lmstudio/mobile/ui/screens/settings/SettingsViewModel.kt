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
    val nGpuLayers: Int = 0,
    val contextSize: Int = 2048,
    val isDarkTheme: Boolean = false,
    val autoSaveChats: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.value = SettingsState(
                nThreads = preferences.getNThreads(),
                nGpuLayers = preferences.getNGpuLayers(),
                contextSize = preferences.getContextSize(),
                isDarkTheme = preferences.isDarkTheme(),
                autoSaveChats = preferences.isAutoSaveChats()
            )
        }
    }

    fun setNThreads(threads: Int) {
        viewModelScope.launch {
            preferences.setNThreads(threads)
            _state.value = _state.value.copy(nThreads = threads)
        }
    }

    fun setNGpuLayers(layers: Int) {
        viewModelScope.launch {
            preferences.setNGpuLayers(layers)
            _state.value = _state.value.copy(nGpuLayers = layers)
        }
    }

    fun setContextSize(size: Int) {
        viewModelScope.launch {
            preferences.setContextSize(size)
            _state.value = _state.value.copy(contextSize = size)
        }
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
