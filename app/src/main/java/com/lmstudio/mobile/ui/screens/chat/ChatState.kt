package com.lmstudio.mobile.ui.screens.chat

import com.lmstudio.mobile.domain.model.Chat
import com.lmstudio.mobile.domain.model.LLMModel

data class ChatState(
    val currentChat: Chat? = null,
    val isModelLoaded: Boolean = false,
    val loadedModel: LLMModel? = null,
    val isGenerating: Boolean = false,
    val error: String? = null
)

