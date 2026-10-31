package com.dukaan.app.di

import com.dukaan.app.core.ai.LiteRTVlmEngine
import com.dukaan.app.core.ai.VLMInferenceEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier to distinguish the VLM-specific OkHttpClient from any future HTTP clients
 * that may be added for other services (e.g. Gemini REST client).
 *
 * GeminiInvoiceService constructs its own OkHttpClient lazily inside the class,
 * so there is no binding conflict. VlmModelManager receives this @VlmHttp-qualified client.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VlmHttp

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    /**
     * Binds [LiteRTVlmEngine] as the active [VLMInferenceEngine] implementation.
     *
     * At runtime, [LiteRTVlmEngine.isReady] returns false until the Gemma 3n model
     * binary is downloaded, causing [InvoiceOcrPipeline] to route transparently to
     * [MlKitFallbackEngine]. No code change is required when the LiteRT LM native
     * runtime is eventually integrated; [executeGemmaInference] will return a real
     * response and [isReady] will return true.
     */
    @Binds
    @Singleton
    abstract fun bindVlmEngine(impl: LiteRTVlmEngine): VLMInferenceEngine

    companion object {

        /**
         * Provides a dedicated [OkHttpClient] for [VlmModelManager] model weight downloads.
         *
         * Uses a generous read timeout (10 min) to support ~1.5 GB binary transfers over
         * slower connections. The connect timeout is kept short to fail fast when offline.
         *
         * This client is intentionally separate from any HTTP client used by [GeminiInvoiceService],
         * which constructs its own client locally and is not managed by Hilt.
         */
        @Provides
        @Singleton
        @VlmHttp
        fun provideVlmOkHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}

