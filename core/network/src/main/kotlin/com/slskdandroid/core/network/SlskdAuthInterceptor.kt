package com.slskdandroid.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retargets every request at the user's configured slskd instance and attaches the API key.
 *
 * Retrofit is built with a placeholder base URL; this interceptor rewrites the scheme, host
 * and port to the configured [SlskdConnectionState.current] base URL (preserving the request
 * path/query) and adds the `X-API-Key` header. If the app is not configured, the request
 * fails fast — callers should gate networking behind a configured connection.
 */
class SlskdAuthInterceptor(
    private val connectionState: SlskdConnectionState,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val settings = connectionState.current
            ?: throw IOException("slskd connection is not configured")

        val base = settings.baseUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid slskd base URL: ${settings.baseUrl}")

        val original = chain.request()
        val newUrl = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()

        val request = original.newBuilder()
            .url(newUrl)
            .header("X-API-Key", settings.apiKey)
            .build()

        return chain.proceed(request)
    }
}
