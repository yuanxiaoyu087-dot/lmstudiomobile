package com.lmstudio.mobile.data.local.database.dao

import androidx.room.*
import com.lmstudio.mobile.data.local.database.entities.ChatFolderCrossRef
import com.lmstudio.mobile.data.local.database.entities.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChatFolderCrossRef(crossRef: ChatFolderCrossRef)

    @Query("DELETE FROM chat_folder_cross_ref WHERE chatId = :chatId AND folderId = :folderId")
    suspend fun removeChatFromFolder(chatId: String, folderId: String)

    @Query("SELECT chatId FROM chat_folder_cross_ref WHERE folderId = :folderId")
    suspend fun getChatIdsByFolder(folderId: String): List<String>

    @Query("SELECT folderId FROM chat_folder_cross_ref WHERE chatId = :chatId")
    suspend fun getFolderIdsByChat(chatId: String): List<String>
}

