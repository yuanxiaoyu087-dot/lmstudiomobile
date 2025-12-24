package com.lmstudio.mobile.data.repository

import com.lmstudio.mobile.data.local.database.dao.ChatDao
import com.lmstudio.mobile.data.local.database.dao.FolderDao
import com.lmstudio.mobile.data.mapper.toDomain
import com.lmstudio.mobile.data.mapper.toEntity
import com.lmstudio.mobile.domain.model.Chat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val folderDao: FolderDao
) {
    fun getAllChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { chats ->
            chats.map { chatEntity ->
                // Note: folderIds will be loaded separately when needed
                chatEntity.toDomain(emptyList())
            }
        }
    }

    suspend fun getChatById(chatId: String): Chat? {
        val chatEntity = chatDao.getChatById(chatId) ?: return null
        val folderIds = folderDao.getFolderIdsByChat(chatId)
        return chatEntity.toDomain(folderIds)
    }

    fun getChatsByModel(modelId: String): Flow<List<Chat>> {
        return chatDao.getChatsByModel(modelId).map { chats ->
            chats.map { chatEntity ->
                val folderIds = folderDao.getFolderIdsByChat(chatEntity.id)
                chatEntity.toDomain(folderIds)
            }
        }
    }

    suspend fun insertChat(chat: Chat) {
        chatDao.insertChat(chat.toEntity())
    }

    suspend fun updateChat(chat: Chat) {
        chatDao.updateChat(chat.toEntity())
    }

    suspend fun hideChat(chatId: String) {
        chatDao.hideChat(chatId)
    }

    suspend fun deleteChat(chatId: String) {
        chatDao.deleteChat(chatId)
    }

    suspend fun renameChat(chatId: String, newTitle: String) {
        val updatedAt = System.currentTimeMillis()
        chatDao.updateChatTitle(chatId, newTitle, updatedAt)
    }

    fun getChatsWithFolders(): Flow<List<Chat>> {
        return getAllChats()
    }
}

