package com.lmstudio.mobile.llm.engine

import com.lmstudio.mobile.llm.inference.InferenceConfig
import com.lmstudio.mobile.llm.monitoring.ResourceMetrics
import kotlinx.coroutines.Job

interface ModelInfo {
    val name: String
    val parameters: String?
    val contextLength: Int
}

interface LLMEngine {
    suspend fun loadModel(modelPath: String, config: InferenceConfig): Result<Unit>
    suspend fun ejectModel(): Result<Unit>
    fun isModelLoaded(): Boolean
    fun generateResponse(
        prompt: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit
    ): Job
    fun stopGeneration()
    fun getModelInfo(): ModelInfo?
    fun getResourceUsage(): ResourceMetrics
}
