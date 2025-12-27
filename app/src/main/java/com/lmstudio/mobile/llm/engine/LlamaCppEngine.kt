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
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

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
        try {
            Log.i(TAG, "loadModel: path=$modelPath, threads=${config.nThreads}, gpuLayers=${config.nGpuLayers}, contextSize=${config.contextSize}")
            val useVulkan = accelerationManager.isVulkanAvailable()
            Log.d(TAG, "loadModel: vulkanAvailable=$useVulkan")
            
            contextPtr = nativeLoadModel(
                modelPath = modelPath,
                nThreads = config.nThreads,
                nGpuLayers = config.nGpuLayers,
                contextSize = config.contextSize,
                useVulkan = useVulkan
            )
            Log.d(TAG, "loadModel: nativeLoadModel returned contextPtr=$contextPtr")
            
            if (contextPtr == 0L) {
                Log.e(TAG, "loadModel: FAILED - contextPtr is 0L (model not loaded)")
                Result.failure(Exception("Failed to load model"))
            } else {
                // Extract model info from path or config
                currentModelInfo = object : ModelInfo {
                    override val name: String = modelPath.substringAfterLast("/")
                    override val parameters: String? = null
                    override val contextLength: Int = config.contextSize
                }
                Log.i(TAG, "loadModel: SUCCESS - model=${currentModelInfo?.name}, contextPtr=$contextPtr")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadModel: EXCEPTION - ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun generateResponse(
        prompt: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit
    ): Job {
        val scope = CoroutineScope(Dispatchers.Default)
        val job = scope.launch {
            // Get current job using coroutineContext - coroutineContext is a built-in property in coroutine scope
            val currentJob = coroutineContext[Job] ?: return@launch
            
            synchronized(this@LlamaCppEngine) {
                currentGenerationJob?.cancel()
                currentGenerationJob = currentJob
            }
            
            try {
                // Capture contextPtr at start - check it throughout
                val currentContextPtr = contextPtr
                if (currentContextPtr == 0L) {
                    Log.e(TAG, "generateResponse: FAILED - Model not loaded (contextPtr=0)")
                    synchronized(this@LlamaCppEngine) {
                        if (currentGenerationJob == currentJob) currentGenerationJob = null
                    }
                    return@launch
                }

                Log.i(TAG, "generateResponse: START - prompt length=${prompt.length}")
                // Reset context for new generation
                if (contextPtr != currentContextPtr || !isActive) {
                    Log.w(TAG, "generateResponse: cancelled before reset")
                    return@launch
                }
                nativeResetContext(currentContextPtr)
                Log.d(TAG, "generateResponse: context reset")
                
                // Limit token generation to prevent crashes
                val maxTokens = 500
                var tokenCount = 0
                
                // First call with prompt to initialize
                Log.d(TAG, "generateResponse: generating first token with prompt")
                if (contextPtr != currentContextPtr || !isActive) {
                    Log.w(TAG, "generateResponse: cancelled before first token")
                    return@launch
                }
                var token = nativeGenerateToken(currentContextPtr, prompt)
                var hasMoreTokens = token.isNotEmpty() && tokenCount < maxTokens
                
                while (hasMoreTokens && isActive) {
                    // Check if context is still valid before each iteration
                    if (contextPtr != currentContextPtr) {
                        Log.w(TAG, "generateResponse: model ejected during generation, stopping")
                        break
                    }
                    
                    if (token.isNotEmpty()) {
                        Log.v(TAG, "generateResponse: token[$tokenCount]='${token.take(20)}'")
                        withContext(Dispatchers.Main) {
                            if (isActive) {
                                onToken(token)
                            }
                        }
                        tokenCount++
                    } else {
                        Log.d(TAG, "generateResponse: empty token at count=$tokenCount")
                    }
                    
                    // Check again before next native call
                    if (contextPtr != currentContextPtr || !isActive) {
                        Log.w(TAG, "generateResponse: cancelled before next token")
                        break
                    }
                    
                    // Continue generation with empty string to get next tokens
                    token = nativeGenerateToken(currentContextPtr, "")
                    hasMoreTokens = token.isNotEmpty() && tokenCount < maxTokens
                    
                    if (tokenCount >= maxTokens) {
                        Log.w(TAG, "generateResponse: reached maxTokens limit ($maxTokens)")
                        break
                    }
                }

                Log.i(TAG, "generateResponse: COMPLETE - totalTokens=$tokenCount")
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onComplete()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "generateResponse: EXCEPTION - ${e.message}", e)
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onComplete()
                    }
                }
            } finally {
                val finalJob = coroutineContext[Job]
                synchronized(this@LlamaCppEngine) {
                    if (finalJob != null && currentGenerationJob == finalJob) {
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
        try {
            Log.i(TAG, "ejectModel: START - contextPtr=$contextPtr")
            
            // Cancel any ongoing generation first
            synchronized(this@LlamaCppEngine) {
                currentGenerationJob?.cancel()
                currentGenerationJob = null
            }
            
            // Wait a bit for cancellation to propagate
            kotlinx.coroutines.delay(50)
            
            val ptrToUnload = contextPtr
            if (ptrToUnload != 0L) {
                // Set contextPtr to 0 before unloading to prevent new calls
                contextPtr = 0L
                currentModelInfo = null
                
                // Now safe to unload native model
                nativeUnloadModel(ptrToUnload)
                Log.d(TAG, "ejectModel: native unload completed")
            }
            Log.i(TAG, "ejectModel: SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "ejectModel: EXCEPTION - ${e.message}", e)
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
                Log.v(TAG, "getResourceUsage: cpu=${usage[0]}, ram=${usage[1]}, vram=${usage[2]}, gpu=${usage[3]}")
                ResourceMetrics(
                    cpuUsage = usage[0],
                    ramUsage = usage[1],
                    vramUsage = usage[2],
                    gpuUsage = usage[3]
                )
            } catch (e: Exception) {
                Log.e(TAG, "getResourceUsage: EXCEPTION - ${e.message}", e)
                ResourceMetrics(0f, 0f, 0f, 0f)
            }
        }
    }
}
