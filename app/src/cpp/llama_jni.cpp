#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"
#include <memory>
#include <mutex>
#include <cstring>
#include <algorithm>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::once_flag backend_init_flag;

struct LlamaContext {
    llama_model* model;
    llama_context* ctx;
    llama_sampler* sampler;
    const llama_vocab* vocab;
    std::vector<llama_token> tokens_list;
    int n_past = 0;
    std::mutex mutex;
    
    LlamaContext() : model(nullptr), ctx(nullptr), sampler(nullptr), vocab(nullptr) {}
    
    ~LlamaContext() {
        if (sampler) llama_sampler_free(sampler);
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
    }
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeLoadModel(
    JNIEnv* env, jobject, jstring modelPath, jint nThreads, jint nGpuLayers, jint contextSize, jboolean useVulkan
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Native loading: %s, threads: %d, layers: %d, ctx: %d", path, nThreads, nGpuLayers, contextSize);
    
    std::call_once(backend_init_flag, []() {
        llama_backend_init();
    });
    
    try {
        llama_model_params mparams = llama_model_default_params();
        mparams.n_gpu_layers = nGpuLayers;
        
        llama_model* model = llama_model_load_from_file(path, mparams);
        if (!model) {
            LOGE("Failed to load model file");
            env->ReleaseStringUTFChars(modelPath, path);
            return 0L;
        }
        
        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx = contextSize;
        cparams.n_threads = nThreads;
        cparams.n_threads_batch = nThreads;
        cparams.n_batch = 512; // Standard batch size
        
        llama_context* ctx = llama_init_from_model(model, cparams);
        if (!ctx) {
            LOGE("Failed to init context");
            llama_model_free(model);
            env->ReleaseStringUTFChars(modelPath, path);
            return 0L;
        }
        
        LlamaContext* llama_ctx = new LlamaContext();
        llama_ctx->model = model;
        llama_ctx->ctx = ctx;
        llama_ctx->vocab = llama_model_get_vocab(model);
        llama_ctx->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(llama_ctx->sampler, llama_sampler_init_greedy());
        
        LOGI("Model loaded pointer: %p", (void*)llama_ctx);
        env->ReleaseStringUTFChars(modelPath, path);
        return reinterpret_cast<jlong>(llama_ctx);
    } catch (const std::exception& e) {
        LOGE("Exception in load: %s", e.what());
        env->ReleaseStringUTFChars(modelPath, path);
        return 0L;
    }
}

JNIEXPORT jstring JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeGenerateToken(
    JNIEnv* env, jobject, jlong contextPtr, jstring prompt
) {
    LlamaContext* llama_ctx = reinterpret_cast<LlamaContext*>(contextPtr);
    if (!llama_ctx || !llama_ctx->ctx) return env->NewStringUTF("");
    
    std::lock_guard<std::mutex> lock(llama_ctx->mutex);

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        return env->NewStringUTF("");
    }
    
    size_t prompt_len = strlen(prompt_str);
    
    // START NEW SESSION
    if (llama_ctx->n_past == 0 && prompt_len > 0) {
        LOGI("Starting new prompt session. Clearing cache.");
        
        // Fully clear KV cache for all sequences
        llama_kv_cache_seq_rm(llama_ctx->ctx, -1, -1, -1);
        llama_sampler_reset(llama_ctx->sampler);
        
        // Tokenize with BOS
        int n_tokens_req = llama_tokenize(llama_ctx->vocab, prompt_str, (int)prompt_len, nullptr, 0, true, true);
        if (n_tokens_req < 0) n_tokens_req = -n_tokens_req;
        
        if (n_tokens_req == 0) {
            env->ReleaseStringUTFChars(prompt, prompt_str);
            return env->NewStringUTF("");
        }
        
        llama_ctx->tokens_list.resize(n_tokens_req);
        int n_tokens = llama_tokenize(llama_ctx->vocab, prompt_str, (int)prompt_len, llama_ctx->tokens_list.data(), n_tokens_req, true, true);
        
        if (n_tokens <= 0) {
            LOGE("Tokenization failed");
            env->ReleaseStringUTFChars(prompt, prompt_str);
            return env->NewStringUTF("");
        }

        int n_ctx = llama_n_ctx(llama_ctx->ctx);
        if (n_tokens >= n_ctx) {
            LOGE("Prompt too long (%d tokens) for context (%d)", n_tokens, n_ctx);
            env->ReleaseStringUTFChars(prompt, prompt_str);
            return env->NewStringUTF("");
        }

        // Process prompt in batches
        int n_batch = llama_n_batch(llama_ctx->ctx);
        for (int i = 0; i < n_tokens; i += n_batch) {
            int n_eval = std::min(n_tokens - i, n_batch);
            
            llama_batch batch = llama_batch_init(n_eval, 0, 1);
            batch.n_tokens = n_eval;
            
            for (int j = 0; j < n_eval; ++j) {
                batch.token[j] = llama_ctx->tokens_list[i + j];
                batch.pos[j] = i + j;
                batch.n_seq_id[j] = 1;
                batch.seq_id[j][0] = 0;
                batch.logits[j] = (i + j == n_tokens - 1);
            }

            int res = llama_decode(llama_ctx->ctx, batch);
            llama_batch_free(batch);
            
            if (res != 0) {
                LOGE("Prompt decode failed: code %d, at pos %d", res, i);
                llama_ctx->n_past = 0;
                env->ReleaseStringUTFChars(prompt, prompt_str);
                return env->NewStringUTF("");
            }
        }
        llama_ctx->n_past = n_tokens;
        LOGI("Prompt processed successfully: %d tokens", n_tokens);
    }
    
    env->ReleaseStringUTFChars(prompt, prompt_str);

    // If we have nothing to generate from, return empty
    if (llama_ctx->n_past == 0) {
        return env->NewStringUTF("");
    }

    // Sample next token
    llama_token id = llama_sampler_sample(llama_ctx->sampler, llama_ctx->ctx, -1);
    
    // Check EOG
    if (llama_vocab_is_eog(llama_ctx->vocab, id)) {
        LOGI("Generation hit EOG");
        return env->NewStringUTF("");
    }

    // Convert to piece
    char buf[256];
    int n = llama_token_to_piece(llama_ctx->vocab, id, buf, sizeof(buf), 0, true);
    if (n < 0) {
        LOGE("Token piece conversion failed");
        return env->NewStringUTF("");
    }
    
    // Add token to KV cache
    if (llama_ctx->n_past + 1 >= llama_n_ctx(llama_ctx->ctx)) {
        LOGE("Context full (%d)", llama_ctx->n_past);
        return env->NewStringUTF("");
    }

    llama_batch batch = llama_batch_init(1, 0, 1);
    batch.n_tokens = 1;
    batch.token[0] = id;
    batch.pos[0] = llama_ctx->n_past;
    batch.n_seq_id[0] = 1;
    batch.seq_id[0][0] = 0;
    batch.logits[0] = true;

    int res = llama_decode(llama_ctx->ctx, batch);
    llama_batch_free(batch);
    
    if (res != 0) {
        LOGE("Token decode failed: code %d at pos %d", res, llama_ctx->n_past);
        // Important: if it fails here, we don't increment n_past.
        // But we should probably return empty to stop generation.
        return env->NewStringUTF("");
    }
    
    llama_ctx->n_past++;
    
    return env->NewStringUTF(std::string(buf, (size_t)n).c_str());
}

JNIEXPORT void JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeUnloadModel(JNIEnv*, jobject, jlong contextPtr) {
    if (contextPtr) {
        LlamaContext* llama_ctx = reinterpret_cast<LlamaContext*>(contextPtr);
        delete llama_ctx;
        LOGI("Native model unloaded");
    }
}

JNIEXPORT void JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeResetContext(JNIEnv*, jobject, jlong contextPtr) {
    LlamaContext* llama_ctx = reinterpret_cast<LlamaContext*>(contextPtr);
    if (!llama_ctx || !llama_ctx->ctx) return;
    
    std::lock_guard<std::mutex> lock(llama_ctx->mutex);
    llama_ctx->n_past = 0;
    llama_ctx->tokens_list.clear();
    
    // The most reliable way to clear all KV cache state
    llama_kv_cache_seq_rm(llama_ctx->ctx, -1, -1, -1);
    llama_sampler_reset(llama_ctx->sampler);
    
    LOGI("KV cache and sampler reset complete");
}

JNIEXPORT jfloatArray JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeGetMemoryUsage(JNIEnv* env, jobject, jlong contextPtr) {
    jfloatArray res = env->NewFloatArray(4);
    float m[4] = {0, 0, 0, 0};
    if (contextPtr) {
        m[0] = 5.0f; 
        m[1] = 512.0f; 
    }
    env->SetFloatArrayRegion(res, 0, 4, m);
    return res;
}

}
