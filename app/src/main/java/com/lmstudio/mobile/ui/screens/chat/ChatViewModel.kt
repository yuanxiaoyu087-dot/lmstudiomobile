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
        Log.i(TAG, "loadChat: $chatId")
        val previousChatId = _currentChatId.value
        _currentChatId.value = chatId
        
        // Очищаем streaming сообщения только для предыдущего чата (если переключаемся между чатами)
        if (previousChatId != null && previousChatId != chatId) {
            _streamingMessages.value = _streamingMessages.value.filter { it.value.chatId == chatId }
            Log.d(TAG, "Cleared streaming messages for previous chat: $previousChatId")
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
            // State collection in observeInferenceState will handle UI update
        }
    }

    fun sendMessage(content: String) {
        Log.i(TAG, "sendMessage called: length=${content.length}, modelLoaded=${inferenceManager.isModelLoaded()}")
        if (content.isBlank() || !inferenceManager.isModelLoaded()) {
            Log.w(TAG, "sendMessage FAILED: blank=${ content.isBlank()}, modelLoaded=${inferenceManager.isModelLoaded()}")
            return
        }

        viewModelScope.launch {
            val autoSave = appPreferences.isAutoSaveChats()
            Log.d(TAG, "autoSave=$autoSave")
            val chatId = _state.value.currentChat?.id ?: run {
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
                // ВАЖНО: Обновляем _currentChatId чтобы messages flow начал работать
                _currentChatId.value = newChat.id
                Log.d(TAG, "Updated _currentChatId to: ${newChat.id}")
                newChat.id
            }

            // Load all previous messages for context
            val previousMessages = if (chatId != "new") {
                messageDao.getMessagesByChat(chatId).first().map { it.toDomain() }
            } else {
                emptyList()
            }
            Log.d(TAG, "previousMessages count: ${previousMessages.size}")

            val userMessage = Message(
                id = java.util.UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.USER,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            
            Log.d(TAG, "User message created: id=${userMessage.id}")
            
            // Добавляем пользовательское сообщение в streaming сразу для отображения
            _streamingMessages.value = _streamingMessages.value + (userMessage.id to userMessage)
            
            if (autoSave) {
                messageDao.insertMessage(userMessage.toEntity())
                Log.d(TAG, "User message saved to database")
                // Оставляем в streaming - логика объединения правильно обработает дубликаты
                // (streaming версия имеет приоритет, а после обновления БД оно автоматически заменится)
            }

            _state.value = _state.value.copy(isGenerating = true)
            Log.i(TAG, "Starting inference generation")

            var assistantContent = ""
            val assistantMessageId = java.util.UUID.randomUUID().toString()
            val assistantTimestamp = System.currentTimeMillis()

            // Создаем пустое сообщение ассистента для streaming
            val assistantMessage = Message(
                id = assistantMessageId,
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                content = "",
                timestamp = assistantTimestamp
            )
            _streamingMessages.value = _streamingMessages.value + (assistantMessageId to assistantMessage)

            // Pass all messages including the new user message
            val allMessages = previousMessages + userMessage
            Log.d(TAG, "Total messages for inference: ${allMessages.size}")

            inferenceManager.generateCompletion(
                messages = allMessages,
                onToken = { token ->
                    Log.v(TAG, "Received token: '${token.take(20)}'")
                    assistantContent += token
                    // Обновляем streaming сообщение в реальном времени
                    _streamingMessages.value = _streamingMessages.value + (assistantMessageId to Message(
                        id = assistantMessageId,
                        chatId = chatId,
                        role = MessageRole.ASSISTANT,
                        content = assistantContent,
                        timestamp = assistantTimestamp
                    ))
                },
                onComplete = {
                    Log.i(TAG, "Inference complete, saving response (length=${assistantContent.length})")
                    viewModelScope.launch {
                        val finalAssistantMessage = Message(
                            id = assistantMessageId,
                            chatId = chatId,
                            role = MessageRole.ASSISTANT,
                            content = assistantContent,
                            timestamp = assistantTimestamp
                        )
                        
                        if (autoSave) {
                            Log.d(TAG, "Saving assistant message to database")
                            messageDao.insertMessage(finalAssistantMessage.toEntity())
                            // Update chat timestamp
                            chatRepository.renameChat(chatId, _state.value.currentChat?.title ?: content.take(50))
                            Log.d(TAG, "Chat timestamp updated")
                            // Удаляем из streaming после сохранения
                            _streamingMessages.value = _streamingMessages.value - assistantMessageId
                        } else {
                            // Если не сохраняем, оставляем в streaming
                            _streamingMessages.value = _streamingMessages.value + (assistantMessageId to finalAssistantMessage)
                        }
                        
                        _state.value = _state.value.copy(isGenerating = false)
                        Log.i(TAG, "sendMessage COMPLETE")
                    }
                }
            )
        }
    }

    fun renameChat(newTitle: String) {
        Log.i(TAG, "renameChat: $newTitle")
        viewModelScope.launch {
            val chatId = _state.value.currentChat?.id ?: return@launch
            chatRepository.renameChat(chatId, newTitle)
            Log.d(TAG, "Chat renamed: $chatId")
        }
    }

    fun deleteChat() {
        Log.i(TAG, "deleteChat called")
        viewModelScope.launch {
            val chatId = _state.value.currentChat?.id ?: return@launch
            chatRepository.deleteChat(chatId)
            Log.d(TAG, "Chat deleted: $chatId")
        }
    }

    private fun updateModelStatus(inferenceState: InferenceState) {
        Log.d(TAG, "updateModelStatus: $inferenceState")
        viewModelScope.launch {
            val isLoaded = (inferenceState == InferenceState.READY || inferenceState == InferenceState.GENERATING)
            if (isLoaded) {
                val loadedModel = modelRepository.getLoadedModel()
                Log.i(TAG, "Model is LOADED: ${loadedModel?.name}")
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
                Log.i(TAG, "Model is NOT LOADED, lastUsed=${lastUsed?.name}")
                _state.value = _state.value.copy(
                    isModelLoaded = false,
                    loadedModel = lastUsed,
                    isLoading = (inferenceState == InferenceState.LOADING)
                )
            }
        }
    }
}
