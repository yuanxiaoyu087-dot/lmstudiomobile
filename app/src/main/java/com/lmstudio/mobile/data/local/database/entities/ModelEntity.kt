package com.lmstudio.mobile.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val format: String, // GGUF, GGML, PYTORCH
    val size: Long,
    val quantization: String?, // Q4_K_M, Q5_K_S, etc.
    val parameters: String?, // 7B, 13B, etc.
    val contextLength: Int,
    val addedAt: Long,
    val isLoaded: Boolean = false,
    val huggingFaceId: String? = null
)

