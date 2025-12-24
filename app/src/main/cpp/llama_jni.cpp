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
    
    if (llama_ctx->n_past == 0) {
        int n_tokens = -llama_tokenize(llama_ctx->vocab, prompt_str, strlen(prompt_str), nullptr, 0, true, true);
        llama_ctx->tokens_list.resize(n_tokens);
        llama_tokenize(llama_ctx->vocab, prompt_str, strlen(prompt_str), llama_ctx->tokens_list.data(), n_tokens, true, true);
    }
    env->ReleaseStringUTFChars(prompt, prompt_str);

    if (llama_ctx->tokens_list.empty()) return env->NewStringUTF("");

    llama_batch batch = llama_batch_init(llama_ctx->tokens_list.size(), 0, 1);
    for (int i = 0; i < (int)llama_ctx->tokens_list.size(); ++i) {
        batch.token[i] = llama_ctx->tokens_list[i];
        batch.pos[i] = llama_ctx->n_past + i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == (int)llama_ctx->tokens_list.size() - 1);
    }

    if (llama_decode(llama_ctx->ctx, batch)) {
        LOGE("Decode failed");
        llama_batch_free(batch);
        return env->NewStringUTF("");
    }
    
    llama_ctx->n_past += llama_ctx->tokens_list.size();
    llama_ctx->tokens_list.clear();
    llama_batch_free(batch);

    llama_token id = llama_sampler_sample(llama_ctx->sampler, llama_ctx->ctx, -1);
    if (llama_vocab_is_eog(llama_ctx->vocab, id)) return env->NewStringUTF("");

    char buf[128];
    int n = llama_token_to_piece(llama_ctx->vocab, id, buf, sizeof(buf), 0, true);
    if (n < 0) return env->NewStringUTF("");
    
    llama_ctx->tokens_list.push_back(id);
    return env->NewStringUTF(std::string(buf, n).c_str());
}

JNIEXPORT void JNICALL
Java_com_lmstudio_mobile_llm_engine_LlamaCppEngine_nativeUnloadModel(JNIEnv*, jobject, jlong contextPtr) {
    if (contextPtr) {
        delete reinterpret_cast<LlamaContext*>(contextPtr);
        LOGI("Native model deleted");
    }
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
