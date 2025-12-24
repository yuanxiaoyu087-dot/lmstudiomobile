package com.lmstudio.mobile.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmstudio.mobile.data.repository.ChatRepository
import com.lmstudio.mobile.domain.model.Chat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryState(
    val chats: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    private var navigateToChatId: String? = null
    val navigateToChat: String?
        get() = navigateToChatId.also { navigateToChatId = null }

    init {
        loadChats()
    }

    private fun loadChats() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                chatRepository.getAllChats().collect { chats ->
                    _state.value = _state.value.copy(
                        chats = chats.sortedByDescending { it.updatedAt },
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun navigateToChat(chatId: String) {
        navigateToChatId = chatId
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(chatId)
        }
    }

    fun deleteAllChats() {
        viewModelScope.launch {
            _state.value.chats.forEach { chat ->
                chatRepository.deleteChat(chat.id)
            }
        }
    }
}
