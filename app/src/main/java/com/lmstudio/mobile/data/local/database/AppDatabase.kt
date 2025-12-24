package com.lmstudio.mobile.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lmstudio.mobile.data.local.database.dao.ChatDao
import com.lmstudio.mobile.data.local.database.dao.FolderDao
import com.lmstudio.mobile.data.local.database.dao.MessageDao
import com.lmstudio.mobile.data.local.database.dao.ModelDao
import com.lmstudio.mobile.data.local.database.entities.ChatEntity
import com.lmstudio.mobile.data.local.database.entities.FolderEntity
import com.lmstudio.mobile.data.local.database.entities.MessageEntity
import com.lmstudio.mobile.data.local.database.entities.ModelEntity
import com.lmstudio.mobile.data.local.database.entities.ChatFolderCrossRef

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        ModelEntity::class,
        FolderEntity::class,
        ChatFolderCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun modelDao(): ModelDao
    abstract fun folderDao(): FolderDao
}

