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
    LOGI("Native loading: %s", path);
    
    try {
        llama_backend_init();
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
    if (!llama_ctx) return env->NewStringUTF("");
    std::lock_guard<std::mutex> lock(llama_ctx->mutex);

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        return env->NewStringUTF("");
    }
    
    bool isPromptEmpty = (strlen(prompt_str) == 0);
    
    // If this is the first call (n_past == 0) and prompt is not empty, tokenize and decode the prompt
    if (llama_ctx->n_past == 0) {
        if (isPromptEmpty) {
            // Empty prompt on first call - nothing to do
            env->ReleaseStringUTFChars(prompt, prompt_str);
            return env->NewStringUTF("");
        }
        
        int n_tokens = -llama_tokenize(llama_ctx->vocab, prompt_str, strlen(prompt_str), nullptr, 0, true, true);
        if (n_tokens <= 0) {
            env->ReleaseStringUTFChars(prompt, prompt_str);
            return env->NewStringUTF("");
        }
        
        llama_ctx->tokens_list.resize(n_tokens);
        llama_tokenize(llama_ctx->vocab, prompt_str, strlen(prompt_str), llama_ctx->tokens_list.data(), n_tokens, true, true);
        
        // Decode the prompt tokens
        llama_batch batch = llama_batch_init(n_tokens, 0, 1);
        batch.n_tokens = n_tokens;
        for (int i = 0; i < n_tokens; ++i) {
            batch.token[i] = llama_ctx->tokens_list[i];
            batch.pos[i] = i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = (i == n_tokens - 1);
        }

        int decode_result = llama_decode(llama_ctx->ctx, batch);
        if (decode_result != 0) {
            LOGE("Decode failed with code: %d", decode_result);
            llama_batch_free(batch);
            llama_ctx->tokens_list.clear();
            llama_ctx->n_past = 0;
            env->ReleaseStringUTFChars(prompt, prompt_str);
            return env->NewStringUTF("");
        }
        
        llama_ctx->n_past = n_tokens;
        llama_batch_free(batch);
    }
    
    env->ReleaseStringUTFChars(prompt, prompt_str);

    // Sample next token from the logits
    llama_token id = llama_sampler_sample(llama_ctx->sampler, llama_ctx->ctx, -1);
    
    // Check for EOG token
    if (llama_vocab_is_eog(llama_ctx->vocab, id)) {
        return env->NewStringUTF("");
    }

    // Convert token to text before adding to context
    char buf[128];
    int n = llama_token_to_piece(llama_ctx->vocab, id, buf, sizeof(buf), 0, true);
    if (n < 0) {
        return env->NewStringUTF("");
    }
    
    // Add the generated token to the context for next generation
    llama_batch batch = llama_batch_init(1, 0, 1);
    batch.n_tokens = 1;
    batch.token[0] = id;
    batch.pos[0] = llama_ctx->n_past;
    batch.n_seq_id[0] = 1;
    batch.seq_id[0][0] = 0;
    batch.logits[0] = true;

    int decode_result = llama_decode(llama_ctx->ctx, batch);
    if (decode_result != 0) {
        LOGE("Decode failed for generated token with code: %d", decode_result);
        llama_batch_free(batch);
        return env->NewStringUTF("");
    }
    
    llama_ctx->n_past++;
    llama_batch_free(batch);
    
    return env->NewStringUTF(std::string(buf, n).c_str());
}

JNIEXPORT void JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeUnloadModel(JNIEnv*, jobject, jlong contextPtr) {
    if (contextPtr) {
        // Note: The Kotlin side should cancel any ongoing operations before calling this.
        // We don't lock here because we can't safely delete an object while holding its own mutex.
        LlamaContext* llama_ctx = reinterpret_cast<LlamaContext*>(contextPtr);
        delete llama_ctx;
        LOGI("Native model deleted");
    }
}

JNIEXPORT void JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeResetContext(JNIEnv*, jobject, jlong contextPtr) {
    LlamaContext* llama_ctx = reinterpret_cast<LlamaContext*>(contextPtr);
    if (!llama_ctx) return;
    std::lock_guard<std::mutex> lock(llama_ctx->mutex);
    llama_ctx->n_past = 0;
    llama_ctx->tokens_list.clear();
}

JNIEXPORT jfloatArray JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeGetMemoryUsage(JNIEnv* env, jobject, jlong contextPtr) {
    jfloatArray res = env->NewFloatArray(4);
    float m[4] = {0, 0, 0, 0};
    if (contextPtr) m[1] = 0.7f; // Simulation
    env->SetFloatArrayRegion(res, 0, 4, m);
    return res;
}

}
