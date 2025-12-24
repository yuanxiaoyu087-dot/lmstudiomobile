package com.lmstudio.mobile.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class HFModelDto(
    val id: String,
    val downloads: Long = 0,
    val likes: Long = 0,
    val tags: List<String> = emptyList(),
    val pipeline_tag: String? = null,
    val model_index: String? = null
) {
    val downloadUrl: String
        get() = "https://huggingface.co/$id/resolve/main/"
    
    val sizeBytes: Long
        get() = 0L // Will be fetched from model details
}

@Serializable
data class ModelDetailsDto(
    val id: String,
    val siblings: List<ModelFile> = emptyList(),
    val tags: List<String> = emptyList()
)

@Serializable
data class ModelFile(
    val rfilename: String,
    val size: Long? = null
)

