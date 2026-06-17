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

    @Provides
    @Singleton
    fun providesOkHttpClient(connectionState: SlskdConnectionState): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(SlskdAuthInterceptor(connectionState))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
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
}
