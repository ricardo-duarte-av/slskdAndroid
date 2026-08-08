package com.slskdandroid.core.data

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.Search
import com.slskdandroid.core.model.SearchResponse
import com.slskdandroid.core.model.SearchResultFile
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.model.DirectoryContentsRequest
import com.slskdandroid.core.network.model.NetworkFile
import com.slskdandroid.core.network.model.NetworkSearch
import com.slskdandroid.core.network.model.NetworkSearchResponse
import com.slskdandroid.core.network.model.StartSearchRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class DefaultSearchRepository @Inject constructor(
    private val api: SlskdApi,
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

    override fun observeSearch(id: String): Flow<SearchProgress> = flow {
        // Polls the search + its responses over REST until slskd reports it complete. slskd's
        // `/responses` endpoint returns the accumulated responses (keyed by peer, deduped) at any
        // point during an in-progress search, so each poll yields a growing snapshot. We poll
        // rather than stream the SignalR search hub: the Microsoft SignalR Java client's
        // long-polling handshake fails against slskd when the hub is actively broadcasting (it
        // batches the handshake response with hub messages), which broke opening an ongoing search.
        var emittedOnce = false
        // Ticks spent waiting for a *completed* search's responses to show up. See below.
        var settlingTicks = 0
        while (currentCoroutineContext().isActive) {
            val poll = runCatching {
                val search = api.getSearch(id)
                val responses = api.getSearchResponses(id).map { it.toModel() }
                search to responses
            }.onFailure {
                // Surface the first failure (nothing shown yet); tolerate transient poll errors
                // once results are on screen and retry on the next tick.
                if (!emittedOnce) throw it
            }.getOrNull()

            if (poll != null) {
                val (search, responses) = poll
                // slskd sets the Completed flag from its search StateChanged callback and persists
                // the record *there*, then attaches `search.Responses` afterwards during
                // finalization. So `isComplete` can be true for a tick or two while /responses still
                // returns nothing — and a search started from this app lands in that window almost
                // every time, because we navigate to the detail screen the instant the POST returns.
                //
                // Treating that first complete-but-empty read as final is what stranded the screen
                // on "no results" until it was closed and reopened. Keep polling until the responses
                // we can see match the count slskd reports, and keep reporting the search as still
                // running so the UI holds its progress indicator (which is what slskd's own web UI
                // does) rather than flashing an empty result set.
                val settled = responses.size >= search.responseCount
                if (search.isComplete && !settled) settlingTicks++

                val done = search.isComplete && (settled || settlingTicks >= MAX_SETTLING_TICKS)
                emittedOnce = true
                emit(SearchProgress(responses, isComplete = done))
                if (done) return@flow
            }
            delay(POLL_INTERVAL_MS)
        }
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

/**
 * How many polls to keep making after slskd reports a search complete while its response count and
 * the responses it actually serves still disagree. Bounds the wait so a search whose responses never
 * materialize (slskd logs a "record may be left 'hanging'" case when finalization throws) settles on
 * whatever it has instead of polling forever. 15 ticks ≈ 30s.
 */
private const val MAX_SETTLING_TICKS = 15
