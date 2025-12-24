package com.lmstudio.mobile.llm.inference

import com.lmstudio.mobile.domain.model.Message
import com.lmstudio.mobile.llm.engine.LLMEngine
import com.lmstudio.mobile.llm.engine.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InferenceManager @Inject constructor(
    private val llmEngine: LLMEngine
) {
    private val _state = MutableStateFlow(InferenceState.IDLE)
    val state: StateFlow<InferenceState> = _state.asStateFlow()

    fun isModelLoaded(): Boolean = llmEngine.isModelLoaded()

    fun getModelInfo(): ModelInfo? = llmEngine.getModelInfo()

    suspend fun loadModel(modelPath: String, config: InferenceConfig): Result<Unit> {
        _state.value = InferenceState.LOADING
        return llmEngine.loadModel(modelPath, config).also { result ->
            _state.value = if (result.isSuccess) InferenceState.READY else InferenceState.ERROR
        }
    }

    suspend fun ejectModel(): Result<Unit> {
        _state.value = InferenceState.IDLE
        return llmEngine.ejectModel()
    }

    fun generateCompletion(
        messages: List<Message>,
        onToken: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        if (_state.value != InferenceState.READY) {
            onComplete()
            return
        }

        _state.value = InferenceState.GENERATING
        val prompt = buildPrompt(messages)
        
        llmEngine.generateResponse(
            prompt = prompt,
            onToken = onToken,
            onComplete = {
                _state.value = InferenceState.READY
                onComplete()
            }
        )
    }

    private fun buildPrompt(messages: List<Message>): String {
        return messages.joinToString("\n") { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.USER -> "User: ${message.content}"
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> "Assistant: ${message.content}"
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> "System: ${message.content}"
            }
        } + "\nAssistant:"
    }
}

