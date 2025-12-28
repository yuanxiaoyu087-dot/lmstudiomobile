package com.lmstudio.mobile.llm.engine

import android.util.Log
import com.lmstudio.mobile.llm.acceleration.AccelerationManager
import com.lmstudio.mobile.llm.inference.InferenceConfig
import com.lmstudio.mobile.llm.monitoring.ResourceMetrics
import kotlinx.coroutines.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val TAG = "LlamaCppEngine"

@Singleton
class LlamaCppEngine @Inject constructor(
    private val accelerationManager: AccelerationManager
) : LLMEngine {

    @Volatile
    private var contextPtr: Long = 0L
    private var currentModelInfo: ModelInfo? = null
    @Volatile
    private var currentGenerationJob: Job? = null
    
    private val stateLock = ReentrantReadWriteLock()

    companion object {
        init {
            try {
                System.loadLibrary("llama_jni")
                Log.i(TAG, "Native library 'llama_jni' loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}", e)
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
    private external fun nativeResetContext(contextPtr: Long)

    private external fun nativeGetMemoryUsage(contextPtr: Long): FloatArray

    override suspend fun loadModel(
        modelPath: String,
        config: InferenceConfig
    ): Result<Unit> = withContext(Dispatchers.IO) {
        stateLock.write {
            try {
                if (contextPtr != 0L) {
                    Log.i(TAG, "loadModel: unloading existing model first")
                    nativeUnloadModel(contextPtr)
                    contextPtr = 0L
                }

                Log.i(TAG, "loadModel: path=$modelPath, threads=${config.nThreads}, gpuLayers=${config.nGpuLayers}, contextSize=${config.contextSize}")
                val useVulkan = accelerationManager.isVulkanAvailable()
                
                contextPtr = nativeLoadModel(
                    modelPath = modelPath,
                    nThreads = config.nThreads,
                    nGpuLayers = config.nGpuLayers,
                    contextSize = config.contextSize,
                    useVulkan = useVulkan
                )
                
                if (contextPtr == 0L) {
                    Log.e(TAG, "loadModel: FAILED")
                    Result.failure(Exception("Failed to load model"))
                } else {
                    currentModelInfo = object : ModelInfo {
                        override val name: String = modelPath.substringAfterLast("/")
                        override val parameters: String? = null
                        override val contextLength: Int = config.contextSize
                    }
                    Log.i(TAG, "loadModel: SUCCESS")
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadModel: EXCEPTION", e)
                Result.failure(e)
            }
        }
    }

    override fun generateResponse(
        prompt: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit
    ): Job {
        val scope = CoroutineScope(Dispatchers.Default)
        val job = scope.launch {
            val currentJob = coroutineContext[Job] ?: return@launch
            
            synchronized(this@LlamaCppEngine) {
                currentGenerationJob?.cancel()
                currentGenerationJob = currentJob
            }
            
            var tokenCount = 0
            try {
                val currentContextPtr = stateLock.read { contextPtr }
                if (currentContextPtr == 0L) {
                    Log.e(TAG, "generateResponse: FAILED - Model not loaded")
                    return@launch
                }

                Log.i(TAG, "generateResponse: START - prompt length=${prompt.length}")
                
                stateLock.read {
                    if (contextPtr == currentContextPtr && isActive) {
                        nativeResetContext(currentContextPtr)
                        Log.d(TAG, "generateResponse: context reset")
                    }
                }
                
                val maxTokens = 1000
                
                var token = stateLock.read {
                    if (contextPtr == currentContextPtr && isActive) {
                        nativeGenerateToken(currentContextPtr, prompt)
                    } else ""
                }
                
                var hasMoreTokens = token.isNotEmpty() && tokenCount < maxTokens
                
                while (hasMoreTokens && isActive) {
                    if (token.isNotEmpty()) {
                        Log.v(TAG, "generateResponse: token[$tokenCount]='${token.take(20)}'")
                        withContext(Dispatchers.Main) {
                            if (isActive) onToken(token)
                        }
                        tokenCount++
                    }
                    
                    if (!isActive) break
                    
                    token = stateLock.read {
                        if (contextPtr == currentContextPtr && isActive) {
                            nativeGenerateToken(currentContextPtr, "")
                        } else ""
                    }
                    hasMoreTokens = token.isNotEmpty() && tokenCount < maxTokens
                }

                Log.i(TAG, "generateResponse: LOOP FINISHED - tokens=$tokenCount")
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "generateResponse: EXCEPTION", e)
                }
            } finally {
                Log.i(TAG, "generateResponse: FINISHED - total tokens=$tokenCount")
                // Always call onComplete, even on cancellation, to save partial response
                withContext(NonCancellable + Dispatchers.Main) {
                    onComplete()
                }
                synchronized(this@LlamaCppEngine) {
                    if (currentGenerationJob == coroutineContext[Job]) {
                        currentGenerationJob = null
                    }
                }
            }
        }
        return job
    }

    override fun stopGeneration() {
        Log.i(TAG, "stopGeneration called")
        synchronized(this) {
            currentGenerationJob?.cancel()
            currentGenerationJob = null
        }
    }

    override suspend fun ejectModel(): Result<Unit> = withContext(Dispatchers.IO) {
        stateLock.write {
            try {
                Log.i(TAG, "ejectModel: START - contextPtr=$contextPtr")
                
                synchronized(this@LlamaCppEngine) {
                    currentGenerationJob?.cancel()
                    currentGenerationJob = null
                }
                
                val ptrToUnload = contextPtr
                if (ptrToUnload != 0L) {
                    contextPtr = 0L
                    currentModelInfo = null
                    nativeUnloadModel(ptrToUnload)
                    Log.d(TAG, "ejectModel: native unload completed")
                }
                Log.i(TAG, "ejectModel: SUCCESS")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "ejectModel: EXCEPTION", e)
                Result.failure(e)
            }
        }
    }

    override fun isModelLoaded(): Boolean = contextPtr != 0L

    override fun getModelInfo(): ModelInfo? = currentModelInfo

    override fun getResourceUsage(): ResourceMetrics {
        return stateLock.read {
            if (contextPtr == 0L) {
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
                    ResourceMetrics(0f, 0f, 0f, 0f)
                }
            }
        }
    }
}
