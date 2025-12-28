package com.lmstudio.mobile.ui.screens.chat

import android.util.Log
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ChatViewModel"

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    
    // Время начала текущей сессии для фильтрации контекста
    private var sessionStartTime: Long = System.currentTimeMillis()
    
    // Объединяем сообщения из БД с активным стримингом из InferenceManager
    // Используем activeChatId из InferenceManager как источник правды для определения текущего чата
    val messages = combine(
        inferenceManager.currentChatId, // Используем activeChatId для определения какой чат загружать
        _currentChatId
    ) { activeChatId, localChatId ->
        // Если есть активная генерация, используем её chatId, иначе локальный
        activeChatId ?: localChatId
    }.flatMapLatest { effectiveChatId ->
        if (effectiveChatId != null && effectiveChatId != "new") {
            messageDao.getMessagesByChat(effectiveChatId).map { messages ->
                messages.map { it.toDomain() }
            }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<Message>())
        }
    }.combine(
        combine(
            inferenceManager.streamingContent,
            inferenceManager.currentChatId,
            inferenceManager.activeAssistantMessageId
        ) { streaming, chatId, msgId -> Triple(streaming, chatId, msgId) }
    ) { dbMessages, (streamingContent, activeChatId, activeMsgId) ->
        // Если идет генерация, добавляем стриминг-сообщение
        val messagesWithStreaming = if (activeChatId != null && activeMsgId != null && streamingContent.isNotEmpty()) {
            val streamingMsg = Message(
                id = activeMsgId,
                chatId = activeChatId,
                role = MessageRole.ASSISTANT,
                content = streamingContent,
                timestamp = System.currentTimeMillis() // Используем текущее время для отображения в конце
            )
            // Исключаем из БД сообщение с таким же ID, если оно там вдруг уже есть
            dbMessages.filter { it.id != activeMsgId } + streamingMsg
        } else {
            dbMessages
        }
        
        messagesWithStreaming.sortedBy { it.timestamp }
    }

    init {
        Log.d(TAG, "ChatViewModel initialized")
        
        // Sync local chatId with active generation if any
        val activeChatId = inferenceManager.currentChatId.value
        if (activeChatId != null) {
            Log.d(TAG, "ViewModel picking up active generation in chat: $activeChatId")
            _currentChatId.value = activeChatId
        }
        
        observeInferenceState()
        updateModelStatus(inferenceManager.state.value)
    }

    private fun observeInferenceState() {
        viewModelScope.launch {
            inferenceManager.state.collect { state ->
                Log.d(TAG, "InferenceState changed to: $state")
                updateModelStatus(state)
            }
        }
    }

    fun loadChat(chatId: String) {
        if (_currentChatId.value != chatId) {
            sessionStartTime = System.currentTimeMillis()
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
    }

    fun loadLastUsedModel() {
        viewModelScope.launch {
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
            val currentModel = modelRepository.getLoadedModel()
            if (currentModel != null) {
                appPreferences.setLastUsedModelPath(currentModel.path)
            }
            inferenceManager.ejectModel()
            modelRepository.unloadAllModels()
        }
    }

    fun stopGeneration() {
        inferenceManager.stopGeneration()
    }

    fun sendMessage(content: String) {
        Log.i(TAG, "sendMessage called: length=${content.length}, modelLoaded=${inferenceManager.isModelLoaded()}")
        if (content.isBlank() || !inferenceManager.isModelLoaded() || _state.value.isGenerating) {
            Log.w(TAG, "sendMessage: skipped - blank=${content.isBlank()}, generating=${_state.value.isGenerating}")
            return
        }

        viewModelScope.launch {
            val autoSave = appPreferences.isAutoSaveChats()
            var currentChatId = _currentChatId.value
            
            if (currentChatId == null || currentChatId == "new") {
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
                _currentChatId.value = newChat.id
                currentChatId = newChat.id
            }

            val chatId = currentChatId ?: return@launch

            val maxContextMessages = 20
            val allMessages = messageDao.getMessagesByChat(chatId).first()
                .map { it.toDomain() }
                .sortedByDescending { it.timestamp }
                .take(maxContextMessages)
                .reversed()

            // Собираем контекст из предыдущих сообщений
            val contextMessages = mutableListOf<Message>()
            for (msg in allMessages) {
                // Добавляем сообщения, если в них есть контент
                if (msg.content.isNotBlank()) {
                    contextMessages.add(msg)
                }
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

            val assistantMessageId = java.util.UUID.randomUUID().toString()
            val allMessagesForPrompt = contextMessages + userMessage
            
            Log.d(TAG, "Starting generation with prompt from ${allMessagesForPrompt.size} messages")

            inferenceManager.generateCompletion(
                chatId = chatId,
                assistantMessageId = assistantMessageId,
                messages = allMessagesForPrompt,
                onToken = { /* handled by streaming flow */ },
                onComplete = { finalContent ->
                    Log.i(TAG, "Generation completed, tokens received: ${finalContent.length}")
                    viewModelScope.launch {
                        if (autoSave && finalContent.isNotBlank()) {
                            val finalAssistantMessage = Message(
                                id = assistantMessageId,
                                chatId = chatId,
                                role = MessageRole.ASSISTANT,
                                content = finalContent,
                                timestamp = System.currentTimeMillis()
                            )
                            messageDao.insertMessage(finalAssistantMessage.toEntity())
                            Log.d(TAG, "Saved assistant message to database: id=$assistantMessageId")
                            chatRepository.renameChat(chatId, _state.value.currentChat?.title ?: content.take(50))
                        }
                    }
                }
            )
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
                    isLoading = false,
                    isGenerating = (inferenceState == InferenceState.GENERATING)
                )
            } else {
                val lastUsedPath = appPreferences.getLastUsedModelPath()
                val lastUsed = if (lastUsedPath != null) {
                    modelRepository.getModelByPath(lastUsedPath)
                } else {
                    modelRepository.getLoadedModel()
                }
                _state.value = _state.value.copy(
                    isModelLoaded = false,
                    loadedModel = lastUsed,
                    isLoading = (inferenceState == InferenceState.LOADING),
                    isGenerating = false
                )
            }
        }
    }
}
