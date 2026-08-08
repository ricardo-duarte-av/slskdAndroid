package com.slskdandroid.core.data

import com.slskdandroid.core.model.Search
import com.slskdandroid.core.model.SearchResponse
import com.slskdandroid.core.model.SearchResultFile
import kotlinx.coroutines.flow.Flow

/** Incremental state of a running search: responses gathered so far, plus completion. */
data class SearchProgress(
    val responses: List<SearchResponse>,
    val isComplete: Boolean,
)

/**
 * Coordinates Soulseek searches against the slskd backend. Searches are persisted server-side:
 * [observeSearches] polls the list, [startSearch] kicks a new one off, and [observeSearch] polls
 * one search's responses until it completes.
 */
interface SearchRepository {

    /** A live, polling stream of all searches on the server (newest-first). */
    fun observeSearches(): Flow<List<Search>>

    /** Starts a new search and returns its id (the caller typically navigates to its detail). */
    suspend fun startSearch(query: String): String

    /** Fetches a single search's current state (without responses). */
    suspend fun getSearch(id: String): Search

    /**
     * Streams the responses for an existing search [id]. Emits a growing [SearchProgress] as peers
     * respond (by polling REST) and completes once slskd reports the search finished *and* has
     * served as many responses as it claims to hold.
     *
     * That second condition matters: slskd flags a search Completed from its state-changed callback
     * and only attaches the responses afterwards, so a search opened the moment it starts sees
     * `isComplete` before `/responses` has anything in it. Until the two agree, [SearchProgress
     * .isComplete] stays false so the UI keeps showing progress rather than an empty result set.
     */
    fun observeSearch(id: String): Flow<SearchProgress>

    /** The final responses for a search, fetched from REST. */
    suspend fun getResponses(id: String): List<SearchResponse>

    /**
     * Fetches the full contents of [directory] from [username] (the "search additional files in
     * this directory" action). Returns the directory's files as result files with absolute paths.
     */
    suspend fun getDirectoryFiles(username: String, directory: String): List<SearchResultFile>

    /** Cancels an in-progress search without deleting it. */
    suspend fun cancelSearch(id: String)

    /** Deletes a search and its responses from the server. */
    suspend fun deleteSearch(id: String)
}
