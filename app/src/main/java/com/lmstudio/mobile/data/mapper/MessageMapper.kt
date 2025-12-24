package com.lmstudio.mobile.data.mapper

import com.lmstudio.mobile.data.local.database.entities.MessageEntity
import com.lmstudio.mobile.domain.model.Message
import com.lmstudio.mobile.domain.model.MessageRole

fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        chatId = chatId,
        role = when (role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            else -> MessageRole.USER
        },
        content = content,
        timestamp = timestamp,
        tokenCount = tokenCount
    )
}

fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        id = id,
        chatId = chatId,
        role = role.name.lowercase(),
        content = content,
        timestamp = timestamp,
        tokenCount = tokenCount
    )
}

