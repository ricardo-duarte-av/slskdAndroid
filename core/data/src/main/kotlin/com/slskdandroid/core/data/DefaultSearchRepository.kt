package com.slskdandroid.core.data

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.SearchResponse
import com.slskdandroid.core.model.SearchResultFile
import com.slskdandroid.core.network.SearchHubEvent
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.SlskdSearchHub
import com.slskdandroid.core.network.model.NetworkSearchResponse
import com.slskdandroid.core.network.model.StartSearchRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal class DefaultSearchRepository @Inject constructor(
    private val api: SlskdApi,
    private val searchHub: SlskdSearchHub,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {

    override fun search(query: String): Flow<SearchProgress> = channelFlow {
        // Start the search over REST to obtain its id, then stream matching hub events.
        // Responses are keyed by username so re-broadcasts replace rather than duplicate.
        val started = api.startSearch(StartSearchRequest(searchText = query))
        val searchId = started.id
        val responsesByUser = ConcurrentHashMap<String, SearchResponse>()

        send(SearchProgress(responses = emptyList(), isComplete = false))

        launch {
            searchHub.events().collect { event ->
                when (event) {
                    is SearchHubEvent.Response -> if (event.searchId == searchId) {
                        val model = event.response.toModel()
                        responsesByUser[model.username] = model
                        send(SearchProgress(responsesByUser.values.toList(), isComplete = false))
                    }

                    is SearchHubEvent.Update -> if (event.searchId == searchId && event.isComplete) {
                        // Reconcile with the authoritative REST result to catch any responses
                        // that arrived before the hub connection was established.
                        runCatching { api.getSearchResponses(searchId) }
                            .getOrNull()
                            ?.forEach { responsesByUser[it.username] = it.toModel() }
                        send(SearchProgress(responsesByUser.values.toList(), isComplete = true))
                        close()
                    }
                }
            }
        }

        awaitClose()
    }.flowOn(ioDispatcher)
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
