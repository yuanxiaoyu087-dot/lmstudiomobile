package com.lmstudio.mobile.di

import android.content.Context
import androidx.room.Room
import com.lmstudio.mobile.data.local.database.AppDatabase
import com.lmstudio.mobile.data.local.database.dao.ChatDao
import com.lmstudio.mobile.data.local.database.dao.FolderDao
import com.lmstudio.mobile.data.local.database.dao.MessageDao
import com.lmstudio.mobile.data.local.database.dao.ModelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "lm_studio_database"
        ).build()
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideModelDao(database: AppDatabase): ModelDao = database.modelDao()

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()
}
