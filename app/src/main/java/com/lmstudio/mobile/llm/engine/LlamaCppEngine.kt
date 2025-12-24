package com.lmstudio.mobile.llm.engine

import android.util.Log
import com.lmstudio.mobile.llm.acceleration.AccelerationManager
import com.lmstudio.mobile.llm.inference.InferenceConfig
import com.lmstudio.mobile.llm.monitoring.ResourceMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaCppEngine @Inject constructor(
    private val accelerationManager: AccelerationManager
) : LLMEngine {

    private var contextPtr: Long = 0L
    private var currentModelInfo: ModelInfo? = null

    companion object {
        init {
            try {
                System.loadLibrary("llama_jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("LlamaCppEngine", "Failed to load native library", e)
            }
        }
    }

    private external fun nativeLoadModel(
        modelPath: String,
        nThreads: Int,
        nGpuLayers: Int,
        contextSize: Int,
        useVulkan: Boolean
    ): Long

    private external fun nativeGenerateToken(
        contextPtr: Long,
        prompt: String
    ): String

    private external fun nativeUnloadModel(contextPtr: Long)

    private external fun nativeGetMemoryUsage(contextPtr: Long): FloatArray

    override suspend fun loadModel(
        modelPath: String,
        config: InferenceConfig
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val useVulkan = accelerationManager.isVulkanAvailable()
            contextPtr = nativeLoadModel(
                modelPath = modelPath,
                nThreads = config.nThreads,
                nGpuLayers = config.nGpuLayers,
                contextSize = config.contextSize,
                useVulkan = useVulkan
            )
            if (contextPtr == 0L) {
                Result.failure(Exception("Failed to load model"))
            } else {
                // Extract model info from path or config
                currentModelInfo = object : ModelInfo {
                    override val name: String = modelPath.substringAfterLast("/")
                    override val parameters: String? = null
                    override val contextLength: Int = config.contextSize
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("LlamaCppEngine", "Error loading model", e)
            Result.failure(e)
        }
    }

    override fun generateResponse(
        prompt: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit
    ): Job = CoroutineScope(Dispatchers.Default).launch {
        try {
            if (contextPtr == 0L) {
                Log.e("LlamaCppEngine", "Model not loaded")
                return@launch
            }

            var token: String
            do {
                token = nativeGenerateToken(contextPtr, prompt)
                if (token.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onToken(token)
                    }
                }
            } while (token.isNotEmpty())

            withContext(Dispatchers.Main) {
                onComplete()
            }
        } catch (e: Exception) {
            Log.e("LlamaCppEngine", "Error generating response", e)
        }
    }

    override suspend fun ejectModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (contextPtr != 0L) {
                nativeUnloadModel(contextPtr)
                contextPtr = 0L
                currentModelInfo = null
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LlamaCppEngine", "Error ejecting model", e)
            Result.failure(e)
        }
    }

    override fun isModelLoaded(): Boolean = contextPtr != 0L

    override fun getModelInfo(): ModelInfo? = currentModelInfo

    override fun getResourceUsage(): ResourceMetrics {
        return if (contextPtr == 0L) {
            ResourceMetrics(0f, 0f, 0f, 0f)
        } else {
            try {
                val usage = nativeGetMemoryUsage(contextPtr)
                ResourceMetrics(
                    cpuUsage = usage[0],
                    ramUsage = usage[1],
                    vramUsage = usage[2],
                    gpuUsage = usage[3]
                )
            } catch (e: Exception) {
                Log.e("LlamaCppEngine", "Error getting resource usage", e)
                ResourceMetrics(0f, 0f, 0f, 0f)
            }
        }
    }
}

