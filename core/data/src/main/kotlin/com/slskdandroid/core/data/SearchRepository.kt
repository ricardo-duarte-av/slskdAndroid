package com.slskdandroid.core.data

import com.slskdandroid.core.model.SearchResponse

/**
 * Coordinates Soulseek searches against the slskd backend. Starting a search is a REST
 * call; live results arrive over the SignalR search hub. This interface keeps the UI
 * agnostic of that split — see [DefaultSearchRepository].
 */
interface SearchRepository {

    /** Starts a search and returns the responses gathered once it completes. */
    suspend fun search(query: String): List<SearchResponse>
}
