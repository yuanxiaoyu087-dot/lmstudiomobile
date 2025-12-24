package com.lmstudio.mobile.data.mapper

import com.lmstudio.mobile.data.local.database.entities.ChatEntity
import com.lmstudio.mobile.domain.model.Chat

fun ChatEntity.toDomain(folderIds: List<String> = emptyList()): Chat {
    return Chat(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isHidden = isHidden,
        modelId = modelId,
        folderIds = folderIds
    )
}

fun Chat.toEntity(): ChatEntity {
    return ChatEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isHidden = isHidden,
        modelId = modelId
    )
}

