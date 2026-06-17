package com.slskdandroid.feature.search.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> _query.value = action.query
            SearchAction.Submit -> submit()
        }
    }

    private fun submit() {
        val query = _query.value.trim()
        if (query.isEmpty()) return

        searchJob?.cancel()
        _uiState.value = SearchUiState.Searching(responses = emptyList())
        searchJob = searchRepository.search(query)
            .onEach { progress ->
                _uiState.value = if (progress.isComplete) {
                    SearchUiState.Complete(progress.responses)
                } else {
                    SearchUiState.Searching(progress.responses)
                }
            }
            .catch { error ->
                _uiState.value = SearchUiState.Error(error.message ?: "Search failed")
            }
            .launchIn(viewModelScope)
    }
}
