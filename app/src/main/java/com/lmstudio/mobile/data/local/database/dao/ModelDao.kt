package com.lmstudio.mobile.data.local.database.dao

import androidx.room.*
import com.lmstudio.mobile.data.local.database.entities.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY addedAt DESC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :modelId")
    suspend fun getModelById(modelId: String): ModelEntity?

    @Query("SELECT * FROM models WHERE isLoaded = 1 LIMIT 1")
    suspend fun getLoadedModel(): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)

    @Update
    suspend fun updateModel(model: ModelEntity)

    @Query("UPDATE models SET isLoaded = 0")
    suspend fun unloadAllModels()

    @Query("UPDATE models SET isLoaded = 1 WHERE id = :modelId")
    suspend fun setModelLoaded(modelId: String)

    @Query("DELETE FROM models WHERE id = :modelId")
    suspend fun deleteModel(modelId: String)

    @Query("SELECT * FROM models WHERE path = :path")
    suspend fun getModelByPath(path: String): ModelEntity?
}

