package com.slskdandroid.feature.search.impl

import com.slskdandroid.core.data.SearchProgress
import com.slskdandroid.core.data.SearchRepository
import com.slskdandroid.core.model.Search
import com.slskdandroid.core.model.SearchResponse
import com.slskdandroid.core.model.SearchResultFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Test double for [SearchRepository] tailored to what [SearchListViewModel] uses. The list stream
 * and the `startSearch` result are configurable per test; calls to `start`/`delete` are recorded so
 * tests can assert the ViewModel forwarded the right thing. Unused methods fail loudly.
 */
class FakeSearchRepository : SearchRepository {

    /** The stream returned by [observeSearches]; swap it per test (e.g. a failing flow). */
    var searchesFlow: Flow<List<Search>> = flowOf(emptyList())

    /** What [startSearch] returns (or override to throw to exercise the failure path). */
    var startSearchBehavior: suspend (String) -> String = { query -> "id-$query" }

    val startedQueries = mutableListOf<String>()
    val deletedIds = mutableListOf<String>()

    override fun observeSearches(): Flow<List<Search>> = searchesFlow

    override suspend fun startSearch(query: String): String {
        startedQueries += query
        return startSearchBehavior(query)
    }

    override suspend fun deleteSearch(id: String) {
        deletedIds += id
    }

    override suspend fun getSearch(id: String): Search = error("unused by SearchListViewModel")
    override fun observeSearch(id: String): Flow<SearchProgress> = error("unused by SearchListViewModel")
    override suspend fun getResponses(id: String): List<SearchResponse> = error("unused by SearchListViewModel")
    override suspend fun getDirectoryFiles(username: String, directory: String): List<SearchResultFile> =
        error("unused by SearchListViewModel")
    override suspend fun cancelSearch(id: String) = error("unused by SearchListViewModel")
}
