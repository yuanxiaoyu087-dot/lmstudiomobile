package com.lmstudio.mobile.llm.inference

data class InferenceConfig(
    val nThreads: Int = Runtime.getRuntime().availableProcessors(),
    val nGpuLayers: Int = 0,
    val contextSize: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f
)

