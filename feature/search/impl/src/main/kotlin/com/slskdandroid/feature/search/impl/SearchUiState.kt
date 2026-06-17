package com.slskdandroid.feature.search.impl

import com.slskdandroid.core.model.SearchResponse

sealed interface SearchUiState {
    data object Idle : SearchUiState

    /** A search is running. [responses] grows as peers reply; empty until the first arrives. */
    data class Searching(val responses: List<SearchResponse>) : SearchUiState

    /** The search finished with its final set of [responses]. */
    data class Complete(val responses: List<SearchResponse>) : SearchUiState

    data class Error(val message: String) : SearchUiState
}

sealed interface SearchAction {
    data class QueryChanged(val query: String) : SearchAction
    data object Submit : SearchAction
}
