#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"
#include <memory>
#include <mutex>
#include <cstring>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Structure to hold model and context
struct LlamaContext {
    llama_model* model;
    llama_context* ctx;
    llama_sampler* sampler;
    const llama_vocab* vocab;
    std::mutex mutex;
    
    LlamaContext() : model(nullptr), ctx(nullptr), sampler(nullptr), vocab(nullptr) {}
    
    ~LlamaContext() {
        if (sampler) {
            llama_sampler_free(sampler);
        }
        if (ctx) {
            llama_free(ctx);
        }
        if (model) {
            llama_model_free(model);
        }
    }
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeLoadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint nThreads,
    jint nGpuLayers,
    jint contextSize,
    jboolean useVulkan
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);
    
    try {
        // Load dynamic backends
        ggml_backend_load_all();
        
        // Initialize model parameters
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = nGpuLayers;
        
        // Load model
        llama_model* model = llama_model_load_from_file(path, model_params);
        if (model == nullptr) {
            LOGE("Failed to load model from: %s", path);
            env->ReleaseStringUTFChars(modelPath, path);
            return 0L;
        }
        
        // Get vocabulary
        const llama_vocab* vocab = llama_model_get_vocab(model);
        
        // Initialize context parameters
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = contextSize;
        ctx_params.n_batch = 512;
        ctx_params.n_threads = nThreads;
        ctx_params.no_perf = false;
        
        // Create context
        llama_context* ctx = llama_init_from_model(model, ctx_params);
        if (ctx == nullptr) {
            LOGE("Failed to create context");
            llama_model_free(model);
            env->ReleaseStringUTFChars(modelPath, path);
            return 0L;
        }
        
        // Initialize sampler
        auto sparams = llama_sampler_chain_default_params();
        sparams.no_perf = false;
        llama_sampler* sampler = llama_sampler_chain_init(sparams);
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
        
        // Create context wrapper
        LlamaContext* llama_ctx = new LlamaContext();
        llama_ctx->model = model;
        llama_ctx->ctx = ctx;
        llama_ctx->sampler = sampler;
        llama_ctx->vocab = vocab;
        
        LOGI("Model loaded successfully");
        env->ReleaseStringUTFChars(modelPath, path);
        return reinterpret_cast<jlong>(llama_ctx);
        
    } catch (const std::exception& e) {
        LOGE("Exception loading model: %s", e.what());
        env->ReleaseStringUTFChars(modelPath, path);
        return 0L;
    }
}

JNIEXPORT jstring JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeGenerateToken(
    JNIEnv* env,
    jobject /* this */,
    jlong contextPtr,
    jstring prompt
) {
    if (contextPtr == 0L) {
        LOGE("Context is null");
        return env->NewStringUTF("");
    }
    
    LlamaContext* llama_ctx = reinterpret_cast<LlamaContext*>(contextPtr);
    std::lock_guard<std::mutex> lock(llama_ctx->mutex);
    
    try {
        const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
        
        // Tokenize prompt
        const int n_prompt = -llama_tokenize(llama_ctx->vocab, promptStr, strlen(promptStr), nullptr, 0, true, true);
        if (n_prompt <= 0) {
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("");
        }
        
        std::vector<llama_token> prompt_tokens(n_prompt);
        if (llama_tokenize(llama_ctx->vocab, promptStr, strlen(promptStr), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
            LOGE("Failed to tokenize prompt");
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("");
        }
        
        env->ReleaseStringUTFChars(prompt, promptStr);
        
        // Prepare batch
        llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
        
        // Check if model has encoder (for encoder-decoder models)
        if (llama_model_has_encoder(llama_ctx->model)) {
            if (llama_encode(llama_ctx->ctx, batch)) {
                LOGE("Failed to encode");
                return env->NewStringUTF("");
            }
            
            llama_token decoder_start_token_id = llama_model_decoder_start_token(llama_ctx->model);
            if (decoder_start_token_id == LLAMA_TOKEN_NULL) {
                decoder_start_token_id = llama_vocab_bos(llama_ctx->vocab);
            }
            
            batch = llama_batch_get_one(&decoder_start_token_id, 1);
        }
        
        // Decode
        if (llama_decode(llama_ctx->ctx, batch)) {
            LOGE("Failed to decode");
            return env->NewStringUTF("");
        }
        
        // Sample next token
        llama_token new_token_id = llama_sampler_sample(llama_ctx->sampler, llama_ctx->ctx, -1);
        
        // Check for end of generation
        if (llama_vocab_is_eog(llama_ctx->vocab, new_token_id)) {
            return env->NewStringUTF("");
        }
        
        // Convert token to string
        char buf[128];
        int n = llama_token_to_piece(llama_ctx->vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n < 0) {
            LOGE("Failed to convert token to piece");
            return env->NewStringUTF("");
        }
        
        std::string token_str(buf, n);
        return env->NewStringUTF(token_str.c_str());
        
    } catch (const std::exception& e) {
        LOGE("Exception generating token: %s", e.what());
        return env->NewStringUTF("");
    }
}

JNIEXPORT void JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeUnloadModel(
    JNIEnv* env,
    jobject /* this */,
    jlong contextPtr
) {
    if (contextPtr == 0L) {
        return;
    }
    
    LOGI("Unloading model");
    LlamaContext* llama_ctx = reinterpret_cast<LlamaContext*>(contextPtr);
    delete llama_ctx;
}

JNIEXPORT jfloatArray JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeGetMemoryUsage(
    JNIEnv* env,
    jobject /* this */,
    jlong contextPtr
) {
    jfloatArray result = env->NewFloatArray(4);
    float metrics[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    
    if (contextPtr != 0L) {
        LlamaContext* llama_ctx = reinterpret_cast<LlamaContext*>(contextPtr);
        std::lock_guard<std::mutex> lock(llama_ctx->mutex);
        
        if (llama_ctx->ctx) {
            // Get memory usage from llama context
            // Note: These are placeholder values - actual implementation would query llama.cpp
            // For now, return basic metrics
            metrics[0] = 0.0f; // CPU usage - would need system calls
            metrics[1] = 0.0f; // RAM usage - would need system calls
            metrics[2] = 0.0f; // VRAM usage - would need backend queries
            metrics[3] = 0.0f; // GPU usage - would need backend queries
        }
    }
    
    env->SetFloatArrayRegion(result, 0, 4, metrics);
    return result;
}

} // extern "C"
