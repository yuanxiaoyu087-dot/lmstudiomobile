package com.lmstudio.mobile.llm.inference

import android.util.Log
import com.lmstudio.mobile.domain.model.Message
import com.lmstudio.mobile.llm.engine.LLMEngine
import com.lmstudio.mobile.llm.engine.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InferenceManager"

@Singleton
class InferenceManager @Inject constructor(
    private val llmEngine: LLMEngine
) {
    private val _state = MutableStateFlow(InferenceState.IDLE)
    val state: StateFlow<InferenceState> = _state.asStateFlow()

    init {
        Log.d(TAG, "InferenceManager initialized")
    }

    fun isModelLoaded(): Boolean {
        val loaded = llmEngine.isModelLoaded()
        Log.d(TAG, "isModelLoaded check: $loaded")
        return loaded
    }

    fun getModelInfo(): ModelInfo? {
        val info = llmEngine.getModelInfo()
        Log.d(TAG, "getModelInfo: name=${info?.name}, contextLength=${info?.contextLength}")
        return info
    }

    suspend fun loadModel(modelPath: String, config: InferenceConfig): Result<Unit> {
        Log.i(TAG, "loadModel START: path=$modelPath, nThreads=${config.nThreads}, nGpuLayers=${config.nGpuLayers}, contextSize=${config.contextSize}")
        _state.value = InferenceState.LOADING
        return llmEngine.loadModel(modelPath, config).also { result ->
            if (result.isSuccess) {
                Log.i(TAG, "loadModel SUCCESS: ${getModelInfo()?.name}")
                _state.value = InferenceState.READY
            } else {
                Log.e(TAG, "loadModel FAILED: ${result.exceptionOrNull()?.message}")
                _state.value = InferenceState.ERROR
            }
        }
    }

    suspend fun ejectModel(): Result<Unit> {
        Log.i(TAG, "ejectModel START")
        _state.value = InferenceState.IDLE
        return llmEngine.ejectModel().also { result ->
            if (result.isSuccess) {
                Log.i(TAG, "ejectModel SUCCESS")
            } else {
                Log.e(TAG, "ejectModel FAILED: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun stopGeneration() {
        Log.i(TAG, "stopGeneration called")
        llmEngine.stopGeneration()
        if (_state.value == InferenceState.GENERATING) {
            _state.value = InferenceState.READY
        }
    }

    fun generateCompletion(
        messages: List<Message>,
        onToken: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        Log.i(TAG, "generateCompletion START: messageCount=${messages.size}, state=${_state.value}")
        if (_state.value != InferenceState.READY) {
            Log.w(TAG, "generateCompletion FAILED: state is ${_state.value}, expected READY")
            onComplete()
            return
        }

        _state.value = InferenceState.GENERATING
        val prompt = buildPrompt(messages)
        Log.d(TAG, "generateCompletion prompt built (length=${prompt.length})")
        
        llmEngine.generateResponse(
            prompt = prompt,
            onToken = { token ->
                Log.v(TAG, "generateCompletion token: '$token'")
                onToken(token)
            },
            onComplete = {
                Log.i(TAG, "generateCompletion COMPLETE")
                _state.value = InferenceState.READY
                onComplete()
            }
        )
    }

    private fun buildPrompt(messages: List<Message>): String {
        val modelInfo = getModelInfo()
        val modelName = modelInfo?.name?.lowercase() ?: ""
        Log.d(TAG, "buildPrompt: detecting template for model '$modelName' from messages: ${messages.map { "${it.role}:${it.content.take(20)}" }}")
        
        // Detect model type by name and use appropriate format
        val template = when {
            modelName.contains("gemma") -> ChatTemplate.GEMMA
            modelName.contains("llama") && (modelName.contains("3") || modelName.contains("3.1") || modelName.contains("3.2")) -> ChatTemplate.LLAMA3
            modelName.contains("llama") && modelName.contains("2") -> ChatTemplate.LLAMA2
            modelName.contains("mistral") -> ChatTemplate.MISTRAL
            modelName.contains("phi") -> ChatTemplate.PHI
            modelName.contains("qwen") -> ChatTemplate.QWEN
            modelName.contains("deepseek") -> ChatTemplate.DEEPSEEK
            else -> ChatTemplate.UNIVERSAL // Universal format for unknown models
        }
        
        Log.d(TAG, "buildPrompt: using template $template")
        
        return when (template) {
            ChatTemplate.GEMMA -> buildGemmaPrompt(messages)
            ChatTemplate.LLAMA3 -> buildLlama3Prompt(messages)
            ChatTemplate.LLAMA2 -> buildLlama2Prompt(messages)
            ChatTemplate.MISTRAL -> buildMistralPrompt(messages)
            ChatTemplate.PHI -> buildPhiPrompt(messages)
            ChatTemplate.QWEN -> buildQwenPrompt(messages)
            ChatTemplate.DEEPSEEK -> buildDeepSeekPrompt(messages)
            ChatTemplate.UNIVERSAL -> buildUniversalPrompt(messages)
        }
    }
    
    private enum class ChatTemplate {
        GEMMA, LLAMA3, LLAMA2, MISTRAL, PHI, QWEN, DEEPSEEK, UNIVERSAL
    }
    
    private fun buildGemmaPrompt(messages: List<Message>): String {
        val prompt = StringBuilder()
        var systemPrompt = ""
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    prompt.append("<start_of_turn>user\n")
                    if (systemPrompt.isNotEmpty() && prompt.length == "<start_of_turn>user\n".length) {
                        prompt.append(systemPrompt).append("\n\n")
                        systemPrompt = ""
                    }
                    prompt.append(message.content.trim())
                    prompt.append("<end_of_turn>\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append("<start_of_turn>model\n")
                    prompt.append(message.content.trim())
                    prompt.append("<end_of_turn>\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    systemPrompt += message.content.trim()
                }
            }
        }
        
        prompt.append("<start_of_turn>model\n")
        return prompt.toString()
    }
    
    private fun buildLlama3Prompt(messages: List<Message>): String {
        val prompt = StringBuilder()
        prompt.append("<|begin_of_text|>")
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    prompt.append("<|start_header_id|>system<|end_header_id|>\n\n")
                    prompt.append(message.content.trim())
                    prompt.append("<|eot_id|>")
                }
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    prompt.append("<|start_header_id|>user<|end_header_id|>\n\n")
                    prompt.append(message.content.trim())
                    prompt.append("<|eot_id|>")
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                    prompt.append(message.content.trim())
                    prompt.append("<|eot_id|>")
                }
            }
        }
        
        prompt.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return prompt.toString()
    }
    
    private fun buildLlama2Prompt(messages: List<Message>): String {
        val prompt = StringBuilder()
        var hasSystem = false
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    prompt.append("<<SYS>>\n")
                    prompt.append(message.content.trim())
                    prompt.append("\n<</SYS>>\n\n")
                    hasSystem = true
                }
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    if (!hasSystem) prompt.append("<s>")
                    prompt.append("[INST] ")
                    prompt.append(message.content.trim())
                    prompt.append(" [/INST]")
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append(" ")
                    prompt.append(message.content.trim())
                    prompt.append(" </s>")
                }
            }
        }
        
        if (messages.lastOrNull()?.role == com.lmstudio.mobile.domain.model.MessageRole.USER) {
            prompt.append(" ")
        }
        
        return prompt.toString()
    }
    
    private fun buildMistralPrompt(messages: List<Message>): String {
        val prompt = StringBuilder()
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    prompt.append("<s>[INST] ")
                    prompt.append(message.content.trim())
                    prompt.append(" [/INST]")
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append(" ")
                    prompt.append(message.content.trim())
                    prompt.append(" </s>")
                }
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    // Mistral v1 doesn't support system, prepend to first user message
                }
            }
        }
        
        if (messages.lastOrNull()?.role == com.lmstudio.mobile.domain.model.MessageRole.USER) {
            prompt.append(" ")
        }
        
        return prompt.toString()
    }
    
    private fun buildPhiPrompt(messages: List<Message>): String {
        val prompt = StringBuilder()
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    prompt.append("<|system|>\n")
                    prompt.append(message.content.trim())
                    prompt.append("<|end|>\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    prompt.append("<|user|>\n")
                    prompt.append(message.content.trim())
                    prompt.append("<|end|>\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append("<|assistant|>\n")
                    prompt.append(message.content.trim())
                    prompt.append("<|end|>\n")
                }
            }
        }
        
        prompt.append("<|assistant|>\n")
        return prompt.toString()
    }
    
    private fun buildQwenPrompt(messages: List<Message>): String {
        val prompt = StringBuilder()
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    prompt.append("<|im_start|>system\n")
                    prompt.append(message.content.trim())
                    prompt.append("<|im_end|>\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    prompt.append("<|im_start|>user\n")
                    prompt.append(message.content.trim())
                    prompt.append("<|im_end|>\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append("<|im_start|>assistant\n")
                    prompt.append(message.content.trim())
                    prompt.append("<|im_end|>\n")
                }
            }
        }
        
        prompt.append("<|im_start|>assistant\n")
        return prompt.toString()
    }
    
    private fun buildDeepSeekPrompt(messages: List<Message>): String {
        val prompt = StringBuilder()
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    prompt.append(message.content.trim())
                    prompt.append("\n\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    prompt.append("User: ")
                    prompt.append(message.content.trim())
                    prompt.append("\n\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append("Assistant: ")
                    prompt.append(message.content.trim())
                    prompt.append("\n\n")
                }
            }
        }
        
        prompt.append("Assistant: ")
        return prompt.toString()
    }
    
    private fun buildUniversalPrompt(messages: List<Message>): String {
        // Universal format that works with most models
        val prompt = StringBuilder()
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    prompt.append("System: ")
                    prompt.append(message.content.trim())
                    prompt.append("\n\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    prompt.append("User: ")
                    prompt.append(message.content.trim())
                    prompt.append("\n\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append("Assistant: ")
                    prompt.append(message.content.trim())
                    prompt.append("\n\n")
                }
            }
        }
        
        prompt.append("Assistant: ")
        return prompt.toString()
    }
}
