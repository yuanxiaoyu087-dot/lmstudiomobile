package com.lmstudio.mobile.domain.model

data class LLMModel(
    val id: String,
    val name: String,
    val path: String,
    val format: ModelFormat,
    val size: Long,
    val quantization: String?,
    val parameters: String?,
    val contextLength: Int,
    val addedAt: Long,
    val isLoaded: Boolean = false,
    val huggingFaceId: String? = null
)

