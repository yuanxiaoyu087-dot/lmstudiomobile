package com.lmstudio.mobile.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_folder_cross_ref",
    primaryKeys = ["chatId", "folderId"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["folderId"], name = "index_chat_folder_cross_ref_folderId"), Index(value = ["chatId"], name = "index_chat_folder_cross_ref_chatId")]
)
data class ChatFolderCrossRef(
    val chatId: String,
    val folderId: String
)
