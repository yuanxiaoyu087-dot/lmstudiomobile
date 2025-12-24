package com.lmstudio.mobile.data.repository

import com.lmstudio.mobile.data.local.database.dao.FolderDao
import com.lmstudio.mobile.data.local.database.entities.ChatFolderCrossRef
import com.lmstudio.mobile.data.local.database.entities.FolderEntity
import com.lmstudio.mobile.data.mapper.toDomain
import com.lmstudio.mobile.data.mapper.toEntity
import com.lmstudio.mobile.domain.model.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FolderRepository @Inject constructor(
    private val folderDao: FolderDao
) {
    fun getAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllFolders().map { folders ->
            folders.map { folderEntity ->
                // Note: chatIds will be loaded separately when needed
                folderEntity.toDomain(emptyList())
            }
        }
    }

    suspend fun getFolderById(folderId: String): Folder? {
        val folderEntity = folderDao.getFolderById(folderId) ?: return null
        val chatIds = folderDao.getChatIdsByFolder(folderId)
        return folderEntity.toDomain(chatIds)
    }

    suspend fun createFolder(folder: FolderEntity) {
        folderDao.insertFolder(folder)
    }

    suspend fun deleteFolder(folderId: String) {
        folderDao.deleteFolder(folderId)
    }

    suspend fun addChatsToFolder(chatIds: List<String>, folderId: String) {
        chatIds.forEach { chatId ->
            folderDao.insertChatFolderCrossRef(
                ChatFolderCrossRef(chatId = chatId, folderId = folderId)
            )
        }
    }

    suspend fun removeChatFromFolder(chatId: String, folderId: String) {
        folderDao.removeChatFromFolder(chatId, folderId)
    }
}

