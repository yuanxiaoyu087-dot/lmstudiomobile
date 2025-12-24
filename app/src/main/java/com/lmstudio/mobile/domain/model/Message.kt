package com.lmstudio.mobile.domain.model

data class Message(
    val id: String,
    val chatId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val tokenCount: Int = 0
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

