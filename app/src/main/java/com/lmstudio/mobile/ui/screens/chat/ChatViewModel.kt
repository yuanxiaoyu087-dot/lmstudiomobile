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
import com.lmstudio.mobile.llm.inference.InferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val messageDao: MessageDao,
    private val inferenceManager: InferenceManager
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

    fun loadChat(chatId: String) {
        _currentChatId.value = chatId
        viewModelScope.launch {
            if (chatId == "new") {
                _state.value = ChatState()
            } else {
                val chat = chatRepository.getChatById(chatId)
                _state.value = _state.value.copy(currentChat = chat)
            }
            updateModelStatus()
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || !_state.value.isModelLoaded) return

        viewModelScope.launch {
            val chatId = _state.value.currentChat?.id ?: run {
                // Create new chat
                val newChat = com.lmstudio.mobile.domain.model.Chat(
                    id = java.util.UUID.randomUUID().toString(),
                    title = content.take(50),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                chatRepository.insertChat(newChat)
                _state.value = _state.value.copy(currentChat = newChat)
                newChat.id
            }

            // Save user message
            val userMessage = Message(
                id = java.util.UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.USER,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            messageDao.insertMessage(userMessage.toEntity())

            _state.value = _state.value.copy(isGenerating = true)

            var assistantContent = ""
            val assistantMessageId = java.util.UUID.randomUUID().toString()

            inferenceManager.generateCompletion(
                messages = listOf(userMessage),
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
                        messageDao.insertMessage(assistantMessage.toEntity())
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

    private fun updateModelStatus() {
        viewModelScope.launch {
            val loadedModel = modelRepository.getLoadedModel()
            _state.value = _state.value.copy(
                isModelLoaded = inferenceManager.isModelLoaded(),
                loadedModel = loadedModel
            )
        }
    }
}
