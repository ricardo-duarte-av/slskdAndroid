package com.slskdandroid.core.data

import com.slskdandroid.core.model.SearchResponse
import kotlinx.coroutines.flow.Flow

/** Incremental state of a running search: responses gathered so far, plus completion. */
data class SearchProgress(
    val responses: List<SearchResponse>,
    val isComplete: Boolean,
)

/**
 * Coordinates Soulseek searches against the slskd backend. Starting a search is a REST call;
 * results stream in live over the SignalR search hub. The returned [Flow] emits a growing
 * [SearchProgress] as peers respond and completes once slskd reports the search finished.
 */
interface SearchRepository {

    fun search(query: String): Flow<SearchProgress>
}
