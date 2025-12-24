package com.lmstudio.mobile.data.mapper

import com.lmstudio.mobile.data.local.database.entities.FolderEntity
import com.lmstudio.mobile.domain.model.Folder

fun FolderEntity.toDomain(chatIds: List<String> = emptyList()): Folder {
    return Folder(
        id = id,
        name = name,
        createdAt = createdAt,
        color = color,
        chatIds = chatIds
    )
}

fun Folder.toEntity(): FolderEntity {
    return FolderEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        color = color
    )
}

