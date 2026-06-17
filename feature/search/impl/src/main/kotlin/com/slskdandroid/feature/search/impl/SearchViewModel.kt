package com.slskdandroid.feature.search.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> _query.value = action.query
            SearchAction.Submit -> submit()
        }
    }

    private fun submit() {
        val query = _query.value.trim()
        if (query.isEmpty()) return

        _uiState.update { SearchUiState.Loading }
        viewModelScope.launch {
            _uiState.value = runCatching { searchRepository.search(query) }
                .fold(
                    onSuccess = { SearchUiState.Success(it) },
                    onFailure = { SearchUiState.Error(it.message ?: "Search failed") },
                )
        }
    }
}
