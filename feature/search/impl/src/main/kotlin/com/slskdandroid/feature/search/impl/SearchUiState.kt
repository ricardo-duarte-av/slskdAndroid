package com.slskdandroid.feature.search.impl

import com.slskdandroid.core.model.SearchResponse

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val responses: List<SearchResponse>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

sealed interface SearchAction {
    data class QueryChanged(val query: String) : SearchAction
    data object Submit : SearchAction
}
