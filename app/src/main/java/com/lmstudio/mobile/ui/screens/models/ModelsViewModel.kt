package com.lmstudio.mobile.ui.screens.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmstudio.mobile.data.repository.ModelRepository
import com.lmstudio.mobile.domain.model.LLMModel
import com.lmstudio.mobile.domain.model.ModelFormat
import com.lmstudio.mobile.llm.inference.InferenceConfig
import com.lmstudio.mobile.llm.inference.InferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

data class ModelsState(
    val models: List<LLMModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val inferenceManager: InferenceManager,
    private val appPreferences: com.lmstudio.mobile.data.local.preferences.AppPreferences
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
                        appPreferences.setLastUsedModelPath(model.path)
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

    fun importModel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val contentResolver = context.contentResolver
                var fileName = "imported_model_${UUID.randomUUID()}.gguf"
                var fileSize = 0L

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }

                val destinationFile = File(context.getExternalFilesDir("models"), fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val newModel = LLMModel(
                    id = UUID.randomUUID().toString(),
                    name = fileName,
                    path = destinationFile.absolutePath,
                    format = ModelFormat.GGUF,
                    size = destinationFile.length(),
                    quantization = "Unknown",
                    parameters = "Unknown",
                    contextLength = 2048,
                    addedAt = System.currentTimeMillis(),
                    isLoaded = false
                )
                modelRepository.insertModel(newModel)
                _state.value = _state.value.copy(isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to import model: ${e.message}")
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
            val model = modelRepository.getModelById(modelId)
            model?.let {
                val file = File(it.path)
                if (file.exists()) {
                    file.delete()
                }
            }
            modelRepository.deleteModel(modelId)
        }
    }
}
