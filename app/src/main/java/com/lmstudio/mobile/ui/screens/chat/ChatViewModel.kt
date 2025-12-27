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
    
    // Локальное состояние для streaming сообщений (пока не сохранены в БД)
    private val _streamingMessages = MutableStateFlow<Map<String, Message>>(emptyMap())
    
    // Время начала текущей сессии для фильтрации контекста (чтобы старые сообщения не обрабатывались повторно)
    private var sessionStartTime: Long = System.currentTimeMillis()
    
    // Объединяем сообщения из БД с локальными streaming сообщениями
    val messages = combine(
        _currentChatId.flatMapLatest { chatId ->
            if (chatId != null && chatId != "new") {
                messageDao.getMessagesByChat(chatId).map { messages ->
                    messages.map { it.toDomain() }
                }
            } else {
                // Для нового чата возвращаем пустой список из БД, но streaming сообщения будут добавлены
                kotlinx.coroutines.flow.flowOf(emptyList<Message>())
            }
        },
        _streamingMessages,
        _currentChatId
    ) { dbMessages, streamingMessages, chatId ->
        // Фильтруем streaming сообщения по текущему chatId
        val relevantStreamingMessages = if (chatId != null) {
            streamingMessages.values.filter { it.chatId == chatId }
        } else {
            emptyList()
        }
        
        val streamingIds = relevantStreamingMessages.map { it.id }.toSet()
        
        // Фильтруем сообщения из БД, исключая те, что есть в streaming (streaming версия более актуальна)
        val dbMessagesFiltered = dbMessages.filter { it.id !in streamingIds }
        
        // Объединяем и сортируем по timestamp
        (dbMessagesFiltered + relevantStreamingMessages).sortedBy { it.timestamp }
    }

    init {
        Log.d(TAG, "ChatViewModel initialized")
        observeInferenceState()
        // Initial sync
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
        Log.i(TAG, "loadChat: $chatId, current=${_currentChatId.value}")
        
        if (_currentChatId.value != chatId) {
            Log.d(TAG, "Chat ID changed, starting new session")
            sessionStartTime = System.currentTimeMillis()
            
            val previousChatId = _currentChatId.value
            _currentChatId.value = chatId
            
            // Очищаем streaming сообщения только для предыдущего чата
            if (previousChatId != null) {
                _streamingMessages.value = _streamingMessages.value.filter { it.value.chatId == chatId }
            }
            
            // Если это новый чат, очищаем все streaming сообщения
            if (chatId == "new") {
                _streamingMessages.value = emptyMap()
            }
            
            viewModelScope.launch {
                if (chatId == "new") {
                    Log.d(TAG, "Loading new chat")
                    _state.value = _state.value.copy(currentChat = null)
                } else {
                    val chat = chatRepository.getChatById(chatId)
                    Log.d(TAG, "Chat loaded: id=$chatId")
                    _state.value = _state.value.copy(currentChat = chat)
                }
            }
        } else {
            Log.d(TAG, "loadChat: Chat already loaded, skipping session reset")
        }
    }

    fun loadLastUsedModel() {
        Log.i(TAG, "loadLastUsedModel called")
        viewModelScope.launch {
            // Try to get last used model from preferences first
            val lastUsedPath = appPreferences.getLastUsedModelPath()
            Log.d(TAG, "lastUsedPath from preferences: $lastUsedPath")
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
        Log.i(TAG, "ejectModel called")
        viewModelScope.launch {
            // Save current model path before ejecting
            val currentModel = modelRepository.getLoadedModel()
            if (currentModel != null) {
                Log.d(TAG, "Saving model path before eject: ${currentModel.path}")
                appPreferences.setLastUsedModelPath(currentModel.path)
            }
            inferenceManager.ejectModel()
            modelRepository.unloadAllModels()
            Log.i(TAG, "ejectModel complete")
        }
    }

    fun stopGeneration() {
        Log.i(TAG, "stopGeneration called")
        inferenceManager.stopGeneration()
        _state.value = _state.value.copy(isGenerating = false)
    }

    override fun onCleared() {
        super.onCleared()
        // Останавливаем генерацию при закрытии ViewModel (например, при выходе из приложения)
        inferenceManager.stopGeneration()
    }

    fun sendMessage(content: String) {
        Log.i(TAG, "sendMessage called: length=${content.length}, modelLoaded=${inferenceManager.isModelLoaded()}")
        
        // Проверяем, не идет ли уже генерация, чтобы избежать параллельных запросов
        if (content.isBlank() || !inferenceManager.isModelLoaded() || _state.value.isGenerating) {
            Log.w(TAG, "sendMessage BLOCKED: blank=${content.isBlank()}, loaded=${inferenceManager.isModelLoaded()}, generating=${_state.value.isGenerating}")
            return
        }

        viewModelScope.launch {
            val autoSave = appPreferences.isAutoSaveChats()
            var currentChatId = _currentChatId.value
            
            // Если это новый чат, создаем его
            if (currentChatId == null || currentChatId == "new") {
                val newChat = com.lmstudio.mobile.domain.model.Chat(
                    id = java.util.UUID.randomUUID().toString(),
                    title = content.take(50),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    modelId = modelRepository.getLoadedModel()?.id
                )
                if (autoSave) {
                    Log.d(TAG, "Creating new chat: ${newChat.id}")
                    chatRepository.insertChat(newChat)
                }
                _state.value = _state.value.copy(currentChat = newChat)
                _currentChatId.value = newChat.id
                currentChatId = newChat.id
                Log.d(TAG, "New chat created, ID: $currentChatId")
            }

            val chatId = currentChatId ?: return@launch

            // Фильтруем сообщения ТЕКУЩЕЙ сессии для контекста.
            // Включаем только сообщения с ответами, чтобы избежать накопления "битых" промптов.
            val bufferTime = 5000L 
            val allSessionMessages = messageDao.getMessagesByChat(chatId).first()
                .map { it.toDomain() }
                .filter { it.timestamp >= (sessionStartTime - bufferTime) }
                .sortedBy { it.timestamp }

            val contextMessages = mutableListOf<Message>()
            var i = 0
            while (i < allSessionMessages.size) {
                val msg = allSessionMessages[i]
                if (msg.role == MessageRole.USER) {
                    // Ищем соответствующий ответ ассистента, идущий следом
                    val nextMsg = allSessionMessages.getOrNull(i + 1)
                    if (nextMsg != null && nextMsg.role == MessageRole.ASSISTANT && nextMsg.content.isNotBlank()) {
                        contextMessages.add(msg)
                        contextMessages.add(nextMsg)
                        i += 2
                    } else {
                        // Если на сообщение не было ответа (прервано или ошибка), 
                        // пропускаем его, чтобы оно не "накапливалось" в новом промпте.
                        Log.d(TAG, "Skipping unanswered message: ${msg.content.take(20)}")
                        i++
                    }
                } else if (msg.role == MessageRole.SYSTEM) {
                    contextMessages.add(msg)
                    i++
                } else {
                    // Одиночные ответы ассистента без контекста пользователя (быть не должно, но сохраняем структуру)
                    contextMessages.add(msg)
                    i++
                }
            }
            
            Log.d(TAG, "Clean context messages: ${contextMessages.size} (from total ${allSessionMessages.size})")

            val userMessage = Message(
                id = java.util.UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.USER,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            
            // Добавляем сообщение пользователя в UI (через streaming для мгновенного отображения)
            _streamingMessages.value = _streamingMessages.value + (userMessage.id to userMessage)
            
            if (autoSave) {
                messageDao.insertMessage(userMessage.toEntity())
            }

            _state.value = _state.value.copy(isGenerating = true)
            
            var assistantContent = ""
            val assistantMessageId = java.util.UUID.randomUUID().toString()
            val assistantTimestamp = System.currentTimeMillis()

            // Заглушка для ответа ассистента
            val assistantMessage = Message(
                id = assistantMessageId,
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                content = "",
                timestamp = assistantTimestamp
            )
            _streamingMessages.value = _streamingMessages.value + (assistantMessageId to assistantMessage)

            val allMessagesForPrompt = contextMessages + userMessage
            Log.d(TAG, "Starting generation with prompt from ${allMessagesForPrompt.size} messages")

            inferenceManager.generateCompletion(
                messages = allMessagesForPrompt,
                onToken = { token ->
                    assistantContent += token
                    _streamingMessages.value = _streamingMessages.value + (assistantMessageId to Message(
                        id = assistantMessageId,
                        chatId = chatId,
                        role = MessageRole.ASSISTANT,
                        content = assistantContent,
                        timestamp = assistantTimestamp
                    ))
                },
                onComplete = {
                    Log.i(TAG, "Generation completed, tokens received: ${assistantContent.length}")
                    viewModelScope.launch {
                        if (autoSave && assistantContent.isNotBlank()) {
                            val finalAssistantMessage = Message(
                                id = assistantMessageId,
                                chatId = chatId,
                                role = MessageRole.ASSISTANT,
                                content = assistantContent,
                                timestamp = assistantTimestamp
                            )
                            messageDao.insertMessage(finalAssistantMessage.toEntity())
                            chatRepository.renameChat(chatId, _state.value.currentChat?.title ?: content.take(50))
                            _streamingMessages.value = _streamingMessages.value - assistantMessageId
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
