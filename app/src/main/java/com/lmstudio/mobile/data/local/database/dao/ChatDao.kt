package com.lmstudio.mobile.data.local.database.dao

import androidx.room.*
import com.lmstudio.mobile.data.local.database.entities.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE isHidden = 0 ORDER BY updatedAt DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE modelId = :modelId AND isHidden = 0 ORDER BY updatedAt DESC")
    fun getChatsByModel(modelId: String): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("UPDATE chats SET isHidden = 1 WHERE id = :chatId")
    suspend fun hideChat(chatId: String)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)

    @Query("UPDATE chats SET title = :title, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun updateChatTitle(chatId: String, title: String, updatedAt: Long)
}

