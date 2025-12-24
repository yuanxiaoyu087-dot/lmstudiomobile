package com.lmstudio.mobile.data.remote.api

import com.lmstudio.mobile.data.remote.dto.HFModelDto
import com.lmstudio.mobile.data.remote.dto.ModelDetailsDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface HuggingFaceApi {
    @GET("api/models")
    suspend fun searchModels(
        @Query("search") query: String,
        @Query("filter") filter: String = "text-generation",
        @Query("sort") sort: String = "downloads",
        @Query("limit") limit: Int = 50
    ): List<HFModelDto>

    @GET("api/models/{modelId}")
    suspend fun getModelDetails(@Path("modelId", encoded = true) modelId: String): ModelDetailsDto
}
