package com.lmstudio.mobile.domain.model

data class Chat(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isHidden: Boolean = false,
    val modelId: String? = null,
    val folderIds: List<String> = emptyList()
)

