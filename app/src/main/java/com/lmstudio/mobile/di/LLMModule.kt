package com.lmstudio.mobile.di

import android.util.Log
import com.lmstudio.mobile.llm.engine.LLMEngine
import com.lmstudio.mobile.llm.engine.LlamaCppEngine
import com.lmstudio.mobile.llm.inference.InferenceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LLMModule {
    @Binds
    @Singleton
    abstract fun bindLLMEngine(llamaCppEngine: LlamaCppEngine): LLMEngine

    companion object {
        @Provides
        @Singleton
        fun provideInferenceManager(llmEngine: LLMEngine): InferenceManager {
            Log.d("AppStartup", "LLMModule: providing InferenceManager...")
            return InferenceManager(llmEngine)
        }
    }
}
