package com.slskdandroid.core.network

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.ConnectionSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates candidate [ConnectionSettings] against a slskd server. Behind an interface so the
 * data layer that depends on it can be unit-tested with a fake instead of real networking.
 */
interface ConnectionTester {
    suspend fun verify(settings: ConnectionSettings): Result<Unit>
}

/**
 * Real [ConnectionTester]. Validates candidate [ConnectionSettings] before they are persisted by
 * making a single authenticated request to slskd's `GET /api/v0/application` endpoint (which
 * requires auth under any scheme). A 2xx response confirms the URL is reachable and the API key
 * is valid.
 *
 * Uses its own short-lived client (not the app's dynamic client) since the settings under
 * test are not yet active.
 */
@Singleton
class SlskdConnectionTester @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ConnectionTester {
    override suspend fun verify(settings: ConnectionSettings): Result<Unit> = withContext(ioDispatcher) {
        // Built locally (not a class member) so OkHttp stays out of this type's public surface,
        // which keeps consuming modules' annotation processors from needing OkHttp on classpath.
        val client = OkHttpClient.Builder()
            .callTimeout(VERIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        runCatching {
            val url = (settings.baseUrl.trimEnd('/') + "/api/v0/application").toHttpUrlOrNull()
                ?: throw IOException("Invalid slskd base URL")

            val request = Request.Builder()
                .url(url)
                .header("X-API-Key", settings.apiKey)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Unit
                    response.code == 401 || response.code == 403 ->
                        throw IOException("Authentication failed — check the API key")

                    else -> throw IOException("slskd returned HTTP ${response.code}")
                }
            }
        }
    }

    private companion object {
        const val VERIFY_TIMEOUT_SECONDS = 10L
    }
}
