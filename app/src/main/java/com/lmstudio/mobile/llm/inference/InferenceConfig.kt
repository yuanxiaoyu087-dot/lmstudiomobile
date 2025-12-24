package com.lmstudio.mobile.llm.inference

data class InferenceConfig(
    val nThreads: Int = 1,  // Force single-threaded mode to avoid OpenMP thread pool failures
    val nGpuLayers: Int = 0,
    val contextSize: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f
)

