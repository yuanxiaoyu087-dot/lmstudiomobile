package com.lmstudio.mobile.data.mapper

import com.lmstudio.mobile.data.local.database.entities.ModelEntity
import com.lmstudio.mobile.domain.model.LLMModel
import com.lmstudio.mobile.domain.model.ModelFormat

fun ModelEntity.toDomain(): LLMModel {
    return LLMModel(
        id = id,
        name = name,
        path = path,
        format = ModelFormat.valueOf(format),
        size = size,
        quantization = quantization,
        parameters = parameters,
        contextLength = contextLength,
        addedAt = addedAt,
        isLoaded = isLoaded,
        huggingFaceId = huggingFaceId
    )
}

fun LLMModel.toEntity(): ModelEntity {
    return ModelEntity(
        id = id,
        name = name,
        path = path,
        format = format.name,
        size = size,
        quantization = quantization,
        parameters = parameters,
        contextLength = contextLength,
        addedAt = addedAt,
        isLoaded = isLoaded,
        huggingFaceId = huggingFaceId
    )
}

