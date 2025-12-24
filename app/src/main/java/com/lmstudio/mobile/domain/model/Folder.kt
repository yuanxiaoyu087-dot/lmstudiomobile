package com.lmstudio.mobile.domain.model

data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val color: Int? = null,
    val chatIds: List<String> = emptyList()
)

