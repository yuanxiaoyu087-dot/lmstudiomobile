package com.lmstudio.mobile.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmstudio.mobile.data.repository.ModelRepository
import com.lmstudio.mobile.domain.model.LLMModel
import com.lmstudio.mobile.llm.inference.InferenceConfig
import com.lmstudio.mobile.llm.inference.InferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelsState(
    val models: List<LLMModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val inferenceManager: InferenceManager
) : ViewModel() {

    private val _state = MutableStateFlow(ModelsState())
    val state: StateFlow<ModelsState> = _state.asStateFlow()

    init {
        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            modelRepository.getAllModels().collect { models ->
                _state.value = _state.value.copy(models = models)
            }
        }
    }

    fun loadModel(modelId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val model = modelRepository.getModelById(modelId)
            if (model != null) {
                val config = InferenceConfig()
                inferenceManager.loadModel(model.path, config).fold(
                    onSuccess = {
                        modelRepository.setModelLoaded(modelId)
                        _state.value = _state.value.copy(isLoading = false)
                    },
                    onFailure = { error ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                )
            }
        }
    }

    fun ejectModel() {
        viewModelScope.launch {
            inferenceManager.ejectModel()
            modelRepository.unloadAllModels()
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.deleteModel(modelId)
        }
    }
}
