package com.lmstudio.mobile.llm.inference

import android.util.Log
import com.lmstudio.mobile.domain.model.Message
import com.lmstudio.mobile.llm.engine.LLMEngine
import com.lmstudio.mobile.llm.engine.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InferenceManager"

@Singleton
class InferenceManager @Inject constructor(
    private val llmEngine: LLMEngine
) {
    private val _state = MutableStateFlow(InferenceState.IDLE)
    val state: StateFlow<InferenceState> = _state.asStateFlow()

    // Shared state for the currently generating message - used by ChatViewModel
    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private val _activeAssistantMessageId = MutableStateFlow<String?>(null)
    val activeAssistantMessageId: StateFlow<String?> = _activeAssistantMessageId.asStateFlow()

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
        if (_state.value == InferenceState.LOADING) {
            Log.w(TAG, "loadModel: Already loading a model, ignoring request")
            return Result.failure(Exception("Model load already in progress"))
        }
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
        _currentChatId.value = null
        _streamingContent.value = ""
        _activeAssistantMessageId.value = null
        return llmEngine.ejectModel().also { result ->
            if (result.isSuccess) {
                Log.i(TAG, "ejectModel SUCCESS")
            } else {
                Log.e(TAG, "ejectModel FAILED: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun stopGeneration() {
        Log.i(TAG, "stopGeneration called (sending cancel signal to engine)")
        llmEngine.stopGeneration()
        // Note: InferenceState will be updated to READY by the onComplete callback
        // which always runs even on cancellation via NonCancellable block.
    }

    fun generateCompletion(
        chatId: String,
        assistantMessageId: String,
        messages: List<Message>,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit
    ) {
        Log.i(TAG, "generateCompletion START: chatId=$chatId, messageCount=${messages.size}, state=${_state.value}")
        if (_state.value != InferenceState.READY) {
            Log.w(TAG, "generateCompletion FAILED: state is ${_state.value}, expected READY")
            onComplete("")
            return
        }

        _state.value = InferenceState.GENERATING
        _currentChatId.value = chatId
        _activeAssistantMessageId.value = assistantMessageId
        _streamingContent.value = ""
        
        Log.d(TAG, "generateCompletion: state set to GENERATING, chatId=$chatId, assistantMessageId=$assistantMessageId")
        
        val prompt = buildPrompt(messages)
        Log.d(TAG, "generateCompletion prompt built (length=${prompt.length})")
        
        llmEngine.generateResponse(
            prompt = prompt,
            onToken = { token ->
                Log.v(TAG, "generateCompletion token: '${token.take(20)}'")
                _streamingContent.value += token
                onToken(token)
            },
            onComplete = {
                val finalContent = _streamingContent.value
                Log.i(TAG, "generateCompletion COMPLETE: finalContentLength=${finalContent.length}")
                _state.value = InferenceState.READY
                onComplete(finalContent)
                
                // Clear streaming state after a small delay to avoid UI flicker
                // while the database is saving and reloading messages
                MainScope().launch {
                    delay(500)
                    Log.d(TAG, "generateCompletion: clearing streaming state (delayed)")
                    _currentChatId.value = null
                    _activeAssistantMessageId.value = null
                    _streamingContent.value = ""
                }
            }
        )
    }

    private fun buildPrompt(messages: List<Message>): String {
        val modelInfo = getModelInfo()
        val modelName = modelInfo?.name?.lowercase() ?: ""
        Log.d(TAG, "buildPrompt: detecting template for model '$modelName' from messages: ${messages.map { "${it.role}:${it.content.take(100)}" }}")
        
        // Detect model type by name and use appropriate format
        val template = when {
            modelName.contains("deepseek") -> ChatTemplate.DEEPSEEK
            modelName.contains("gemma") -> ChatTemplate.GEMMA
            modelName.contains("llama") && (modelName.contains("3") || modelName.contains("3.1") || modelName.contains("3.2")) -> ChatTemplate.LLAMA3
            modelName.contains("llama") && modelName.contains("2") -> ChatTemplate.LLAMA2
            modelName.contains("mistral") || modelName.contains("mixtral") || modelName.contains("zephyr") -> ChatTemplate.MISTRAL
            modelName.contains("phi") -> ChatTemplate.PHI
            modelName.contains("command-r") || modelName.contains("c4ai") -> ChatTemplate.COMMAND_R
            modelName.contains("vicuna") || modelName.contains("wizardlm") || modelName.contains("alpaca") -> ChatTemplate.VICUNA
            // ChatML detection for many fine-tuned models
            modelName.contains("qwen") || modelName.contains("hermes") || modelName.contains("dolphin") || 
            modelName.contains("yi") || modelName.contains("orca") || modelName.contains("chatml") -> ChatTemplate.QWEN
            else -> ChatTemplate.UNIVERSAL
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
            ChatTemplate.COMMAND_R -> buildCommandRPrompt(messages)
            ChatTemplate.VICUNA -> buildVicunaPrompt(messages)
            ChatTemplate.UNIVERSAL -> buildUniversalPrompt(messages)
        }
    }
    
    private enum class ChatTemplate {
        GEMMA, LLAMA3, LLAMA2, MISTRAL, PHI, QWEN, DEEPSEEK, COMMAND_R, VICUNA, UNIVERSAL
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
        
        // No forced <thought> here, Distill models will generate it naturally
        prompt.append("<|im_start|>assistant\n")
        return prompt.toString()
    }

    private fun buildDeepSeekPrompt(messages: List<Message>): String {
        val modelName = getModelInfo()?.name?.lowercase() ?: ""
        
        // DeepSeek-R1-Distill-Qwen models are essentially Qwen models
        if (modelName.contains("qwen") || modelName.contains("distill")) {
            return buildQwenPrompt(messages)
        }
        
        // Native DeepSeek-V3/R1 Template
        val prompt = StringBuilder()
        // DeepSeek usually doesn't need a BOS in the string itself because llama_tokenize handles it
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    // System messages are often prepended to user prompt in DeepSeek
                    prompt.append(message.content.trim()).append("\n\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    prompt.append("<｜User｜>").append(message.content.trim())
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append("<｜Assistant｜>").append(message.content.trim()).append("<｜end of sentence｜>")
                }
            }
        }
        
        prompt.append("<｜Assistant｜>")
        // For native R1, we can add the thought tag if we want to force reasoning, 
        // but let's see if it works without it first to be safe.
        return prompt.toString()
    }
    
    private fun buildCommandRPrompt(messages: List<Message>): String {
        val prompt = StringBuilder()
        prompt.append("<BOS_TOKEN>")
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    prompt.append("<|START_OF_TURN_TOKEN|><|SYSTEM_TOKEN|>")
                    prompt.append(message.content.trim())
                    prompt.append("<|END_OF_TURN_TOKEN|>")
                }
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    prompt.append("<|START_OF_TURN_TOKEN|><|USER_TOKEN|>")
                    prompt.append(message.content.trim())
                    prompt.append("<|END_OF_TURN_TOKEN|>")
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append("<|START_OF_TURN_TOKEN|><|CHATBOT_TOKEN|>")
                    prompt.append(message.content.trim())
                    prompt.append("<|END_OF_TURN_TOKEN|>")
                }
            }
        }
        
        prompt.append("<|START_OF_TURN_TOKEN|><|CHATBOT_TOKEN|>")
        return prompt.toString()
    }
    
    private fun buildVicunaPrompt(messages: List<Message>): String {
        val prompt = StringBuilder()
        
        messages.forEach { message ->
            when (message.role) {
                com.lmstudio.mobile.domain.model.MessageRole.SYSTEM -> {
                    prompt.append(message.content.trim()).append("\n\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.USER -> {
                    prompt.append("USER: ").append(message.content.trim()).append("\n")
                }
                com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT -> {
                    prompt.append("ASSISTANT: ").append(message.content.trim()).append("</s>\n")
                }
            }
        }
        
        prompt.append("ASSISTANT: ")
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
