package com.slskdandroid.feature.search.impl

import com.slskdandroid.core.model.Search

/** State of the search landing screen: the new-search field plus the list of existing searches. */
data class SearchListUiState(
    val query: String,
    val starting: Boolean,
    val searches: SearchesState,
) {
    companion object {
        val Initial = SearchListUiState(query = "", starting = false, searches = SearchesState.Loading)
    }
}

sealed interface SearchesState {
    data object Loading : SearchesState
    data class Loaded(val searches: List<Search>) : SearchesState
    data class Error(val message: String) : SearchesState
}

sealed interface SearchListAction {
    data class QueryChanged(val query: String) : SearchListAction
    data object Submit : SearchListAction
    data class Delete(val id: String) : SearchListAction
}
