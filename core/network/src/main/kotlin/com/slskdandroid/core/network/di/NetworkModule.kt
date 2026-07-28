package com.slskdandroid.core.network.di

import com.slskdandroid.core.network.BuildConfig
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.SlskdAuthInterceptor
import com.slskdandroid.core.network.SlskdConnectionState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Timeouts are set explicitly rather than left at OkHttp's 10s defaults: browsing a peer's
     * share (`users/{u}/browse`) blocks server-side until the *whole* listing arrives and sends
     * nothing meanwhile, so a large share trivially exceeds a 10s read timeout and surfaces as
     * "Couldn't browse X". The read timeout is therefore generous; connect stays short so an
     * unreachable instance still fails fast.
     */
    @Provides
    @Singleton
    fun providesOkHttpClient(connectionState: SlskdConnectionState): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(SlskdAuthInterceptor(connectionState))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // Never log the API key, even in debug — logcat is world-readable to any
                    // process holding READ_LOGS and these logs get pasted into bug reports.
                    redactHeader("X-API-Key")
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .build()

    @Provides
    @Singleton
    fun providesRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        // Placeholder base URL — SlskdAuthInterceptor rewrites the host/port per request
        // from the user's configured connection settings.
        .baseUrl("http://localhost/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun providesSlskdApi(retrofit: Retrofit): SlskdApi = retrofit.create(SlskdApi::class.java)

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 120L
    private const val WRITE_TIMEOUT_SECONDS = 30L
}
