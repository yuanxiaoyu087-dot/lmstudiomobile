package com.lmstudio.mobile.data.repository

import com.lmstudio.mobile.data.local.database.dao.ModelDao
import com.lmstudio.mobile.data.mapper.toDomain
import com.lmstudio.mobile.data.mapper.toEntity
import com.lmstudio.mobile.domain.model.LLMModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ModelRepository @Inject constructor(
    private val modelDao: ModelDao
) {
    fun getAllModels(): Flow<List<LLMModel>> {
        return modelDao.getAllModels().map { models ->
            models.map { it.toDomain() }
        }
    }

    suspend fun getModelById(modelId: String): LLMModel? {
        return modelDao.getModelById(modelId)?.toDomain()
    }

    suspend fun getLoadedModel(): LLMModel? {
        return modelDao.getLoadedModel()?.toDomain()
    }

    suspend fun insertModel(model: LLMModel) {
        modelDao.insertModel(model.toEntity())
    }

    suspend fun updateModel(model: LLMModel) {
        modelDao.updateModel(model.toEntity())
    }

    suspend fun setModelLoaded(modelId: String) {
        modelDao.unloadAllModels()
        modelDao.setModelLoaded(modelId)
    }

    suspend fun unloadAllModels() {
        modelDao.unloadAllModels()
    }

    suspend fun deleteModel(modelId: String) {
        modelDao.deleteModel(modelId)
    }

    suspend fun getModelByPath(path: String): LLMModel? {
        return modelDao.getModelByPath(path)?.toDomain()
    }
}

