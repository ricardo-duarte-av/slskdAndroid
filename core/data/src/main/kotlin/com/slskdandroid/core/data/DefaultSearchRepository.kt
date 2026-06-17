package com.slskdandroid.core.data

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.SearchResponse
import com.slskdandroid.core.model.SearchResultFile
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.model.NetworkSearchResponse
import com.slskdandroid.core.network.model.StartSearchRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class DefaultSearchRepository @Inject constructor(
    private val api: SlskdApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {

    override suspend fun search(query: String): List<SearchResponse> = withContext(ioDispatcher) {
        val started = api.startSearch(StartSearchRequest(searchText = query))

        // Naive completion poll. A production implementation should subscribe to the
        // slskd SignalR search hub for incremental responses instead of polling.
        var attempts = 0
        while (attempts < MAX_POLL_ATTEMPTS) {
            val state = api.getSearch(started.id)
            if (state.isComplete) break
            delay(POLL_INTERVAL_MS)
            attempts++
        }

        api.getSearchResponses(started.id).map(NetworkSearchResponse::toModel)
    }

    private companion object {
        const val MAX_POLL_ATTEMPTS = 20
        const val POLL_INTERVAL_MS = 1_000L
    }
}

private fun NetworkSearchResponse.toModel() = SearchResponse(
    username = username,
    hasFreeUploadSlot = hasFreeUploadSlot,
    uploadSpeed = uploadSpeed,
    queueLength = queueLength,
    files = files.map { file ->
        SearchResultFile(
            filename = file.filename,
            sizeBytes = file.size,
            bitRate = file.bitRate,
            lengthSeconds = file.length,
        )
    },
)
