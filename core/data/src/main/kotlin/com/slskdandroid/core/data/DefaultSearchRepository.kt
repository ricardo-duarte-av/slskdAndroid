package com.slskdandroid.core.data

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.Search
import com.slskdandroid.core.model.SearchResponse
import com.slskdandroid.core.model.SearchResultFile
import com.slskdandroid.core.network.SearchHubEvent
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.SlskdSearchHub
import com.slskdandroid.core.network.model.DirectoryContentsRequest
import com.slskdandroid.core.network.model.NetworkFile
import com.slskdandroid.core.network.model.NetworkSearch
import com.slskdandroid.core.network.model.NetworkSearchResponse
import com.slskdandroid.core.network.model.StartSearchRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal class DefaultSearchRepository @Inject constructor(
    private val api: SlskdApi,
    private val searchHub: SlskdSearchHub,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {

    override fun observeSearches(): Flow<List<Search>> = flow {
        var emittedOnce = false
        while (currentCoroutineContext().isActive) {
            runCatching { api.getSearches().map { it.toModel() } }
                .onSuccess { emittedOnce = true; emit(it.sortedByDescending { s -> s.startedAt }) }
                .onFailure { if (!emittedOnce) throw it }
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(ioDispatcher)

    override suspend fun startSearch(query: String): String = withContext(ioDispatcher) {
        api.startSearch(StartSearchRequest(searchText = query)).id
    }

    override suspend fun getSearch(id: String): Search =
        withContext(ioDispatcher) { api.getSearch(id).toModel() }

    override fun observeSearch(id: String): Flow<SearchProgress> = channelFlow {
        val responsesByUser = ConcurrentHashMap<String, SearchResponse>()

        // If the search already finished (e.g. opened from history), the hub won't re-broadcast,
        // so resolve it straight from REST and complete.
        val current = runCatching { api.getSearch(id) }.getOrNull()
        if (current?.isComplete == true) {
            api.getSearchResponses(id).forEach { responsesByUser[it.username] = it.toModel() }
            send(SearchProgress(responsesByUser.values.toList(), isComplete = true))
            close()
            return@channelFlow
        }

        send(SearchProgress(emptyList(), isComplete = false))

        launch {
            searchHub.events().collect { event ->
                when (event) {
                    is SearchHubEvent.Response -> if (event.searchId == id) {
                        val model = event.response.toModel()
                        responsesByUser[model.username] = model
                        send(SearchProgress(responsesByUser.values.toList(), isComplete = false))
                    }

                    is SearchHubEvent.Update -> if (event.searchId == id && event.isComplete) {
                        runCatching { api.getSearchResponses(id) }
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

    override suspend fun getResponses(id: String): List<SearchResponse> =
        withContext(ioDispatcher) { api.getSearchResponses(id).map { it.toModel() } }

    override suspend fun getDirectoryFiles(
        username: String,
        directory: String,
    ): List<SearchResultFile> = withContext(ioDispatcher) {
        // Some peers return subdirectories too; the requested root is first. Prefix the base
        // filenames with the directory so they read like search results.
        val root = api.getDirectoryContents(username, DirectoryContentsRequest(directory))
            .firstOrNull() ?: return@withContext emptyList()
        root.files.map { file ->
            file.toModel(isLocked = false).copy(filename = "$directory\\${file.filename}")
        }
    }

    override suspend fun cancelSearch(id: String) {
        withContext(ioDispatcher) { api.cancelSearch(id) }
    }

    override suspend fun deleteSearch(id: String) {
        withContext(ioDispatcher) { api.deleteSearch(id) }
    }
}

private fun NetworkSearch.toModel() = Search(
    id = id,
    searchText = searchText,
    state = state,
    isComplete = isComplete,
    fileCount = fileCount,
    lockedFileCount = lockedFileCount,
    responseCount = responseCount,
    startedAt = startedAt,
    endedAt = endedAt,
)

private fun NetworkSearchResponse.toModel() = SearchResponse(
    username = username,
    hasFreeUploadSlot = hasFreeUploadSlot,
    uploadSpeed = uploadSpeed,
    queueLength = queueLength,
    files = files.map { it.toModel(isLocked = false) },
    lockedFiles = lockedFiles.map { it.toModel(isLocked = true) },
)

private fun NetworkFile.toModel(isLocked: Boolean) = SearchResultFile(
    filename = filename,
    sizeBytes = size,
    bitRate = bitRate,
    lengthSeconds = length,
    bitDepth = bitDepth,
    sampleRate = sampleRate,
    isVariableBitRate = isVariableBitRate,
    extension = extension,
    isLocked = this.isLocked || isLocked,
)

private const val POLL_INTERVAL_MS = 2_000L
