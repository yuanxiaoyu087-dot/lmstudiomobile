package com.lmstudio.mobile.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isHidden: Boolean = false,
    val modelId: String?
)

