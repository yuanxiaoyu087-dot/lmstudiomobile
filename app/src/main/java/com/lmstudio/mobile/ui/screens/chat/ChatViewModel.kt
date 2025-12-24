package com.lmstudio.mobile.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmstudio.mobile.data.local.database.dao.MessageDao
import com.lmstudio.mobile.data.mapper.toDomain
import com.lmstudio.mobile.data.mapper.toEntity
import com.lmstudio.mobile.data.repository.ChatRepository
import com.lmstudio.mobile.data.repository.ModelRepository
import com.lmstudio.mobile.domain.model.Message
import com.lmstudio.mobile.domain.model.MessageRole
import com.lmstudio.mobile.llm.inference.InferenceConfig
import com.lmstudio.mobile.llm.inference.InferenceManager
import com.lmstudio.mobile.llm.inference.InferenceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val messageDao: MessageDao,
    private val inferenceManager: InferenceManager,
    private val appPreferences: com.lmstudio.mobile.data.local.preferences.AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    
    val messages = _currentChatId.flatMapLatest { chatId ->
        if (chatId != null && chatId != "new") {
            messageDao.getMessagesByChat(chatId).map { messages ->
                messages.map { it.toDomain() }
            }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<Message>())
        }
    }

    init {
        observeInferenceState()
        // Initial sync
        updateModelStatus(inferenceManager.state.value)
    }

    private fun observeInferenceState() {
        viewModelScope.launch {
            inferenceManager.state.collect { state ->
                updateModelStatus(state)
            }
        }
    }

    fun loadChat(chatId: String) {
        _currentChatId.value = chatId
        viewModelScope.launch {
            if (chatId == "new") {
                _state.value = _state.value.copy(currentChat = null)
            } else {
                val chat = chatRepository.getChatById(chatId)
                _state.value = _state.value.copy(currentChat = chat)
            }
        }
    }

    fun loadLastUsedModel() {
        viewModelScope.launch {
            // Try to get last used model from preferences first
            val lastUsedPath = appPreferences.getLastUsedModelPath()
            val modelToLoad = if (lastUsedPath != null) {
                modelRepository.getModelByPath(lastUsedPath)
            } else {
                modelRepository.getLoadedModel()
            }
            
            val model = modelToLoad ?: return@launch
            _state.value = _state.value.copy(isLoading = true)
            inferenceManager.loadModel(model.path, InferenceConfig()).onSuccess {
                modelRepository.setModelLoaded(model.id)
                appPreferences.setLastUsedModelPath(model.path)
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun ejectModel() {
        viewModelScope.launch {
            // Save current model path before ejecting
            val currentModel = modelRepository.getLoadedModel()
            if (currentModel != null) {
                appPreferences.setLastUsedModelPath(currentModel.path)
            }
            inferenceManager.ejectModel()
            modelRepository.unloadAllModels()
            // State collection in observeInferenceState will handle UI update
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || !inferenceManager.isModelLoaded()) return

        viewModelScope.launch {
            val autoSave = appPreferences.isAutoSaveChats()
            val chatId = _state.value.currentChat?.id ?: run {
                val newChat = com.lmstudio.mobile.domain.model.Chat(
                    id = java.util.UUID.randomUUID().toString(),
                    title = content.take(50),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    modelId = modelRepository.getLoadedModel()?.id
                )
                if (autoSave) {
                    chatRepository.insertChat(newChat)
                }
                _state.value = _state.value.copy(currentChat = newChat)
                newChat.id
            }

            // Load all previous messages for context
            val previousMessages = if (chatId != "new") {
                messageDao.getMessagesByChat(chatId).first().map { it.toDomain() }
            } else {
                emptyList()
            }

            val userMessage = Message(
                id = java.util.UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.USER,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            
            if (autoSave) {
                messageDao.insertMessage(userMessage.toEntity())
            }

            _state.value = _state.value.copy(isGenerating = true)

            var assistantContent = ""
            val assistantMessageId = java.util.UUID.randomUUID().toString()

            // Pass all messages including the new user message
            val allMessages = previousMessages + userMessage

            inferenceManager.generateCompletion(
                messages = allMessages,
                onToken = { token ->
                    assistantContent += token
                },
                onComplete = {
                    viewModelScope.launch {
                        val assistantMessage = Message(
                            id = assistantMessageId,
                            chatId = chatId,
                            role = MessageRole.ASSISTANT,
                            content = assistantContent,
                            timestamp = System.currentTimeMillis()
                        )
                        if (autoSave) {
                            messageDao.insertMessage(assistantMessage.toEntity())
                            // Update chat timestamp
                            chatRepository.renameChat(chatId, _state.value.currentChat?.title ?: content.take(50))
                        }
                        _state.value = _state.value.copy(isGenerating = false)
                    }
                }
            )
        }
    }

    fun renameChat(newTitle: String) {
        viewModelScope.launch {
            val chatId = _state.value.currentChat?.id ?: return@launch
            chatRepository.renameChat(chatId, newTitle)
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            val chatId = _state.value.currentChat?.id ?: return@launch
            chatRepository.deleteChat(chatId)
        }
    }

    private fun updateModelStatus(inferenceState: InferenceState) {
        viewModelScope.launch {
            val isLoaded = (inferenceState == InferenceState.READY || inferenceState == InferenceState.GENERATING)
            if (isLoaded) {
                val loadedModel = modelRepository.getLoadedModel()
                _state.value = _state.value.copy(
                    isModelLoaded = true,
                    loadedModel = loadedModel,
                    isLoading = false
                )
            } else {
                // Try to get last used model from preferences
                val lastUsedPath = appPreferences.getLastUsedModelPath()
                val lastUsed = if (lastUsedPath != null) {
                    modelRepository.getModelByPath(lastUsedPath)
                } else {
                    modelRepository.getLoadedModel()
                }
                _state.value = _state.value.copy(
                    isModelLoaded = false,
                    loadedModel = lastUsed,
                    isLoading = (inferenceState == InferenceState.LOADING)
                )
            }
        }
    }
}
