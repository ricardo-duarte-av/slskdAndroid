package com.slskdandroid.feature.search.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchListViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val starting = MutableStateFlow(false)
    private val searches = MutableStateFlow<SearchesState>(SearchesState.Loading)

    val uiState: StateFlow<SearchListUiState> =
        combine(query, starting, searches) { q, isStarting, list ->
            SearchListUiState(q, isStarting, list)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchListUiState.Initial,
        )

    // One-shot navigation events: ids of freshly started searches to open.
    private val openChannel = Channel<String>(Channel.BUFFERED)
    val openSearch = openChannel.receiveAsFlow()

    init {
        searchRepository.observeSearches()
            .onEach { searches.value = SearchesState.Loaded(it) }
            .catch { searches.value = SearchesState.Error(it.message ?: "Couldn't load searches") }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SearchListAction) {
        when (action) {
            is SearchListAction.QueryChanged -> query.value = action.query
            SearchListAction.Submit -> submit()
            is SearchListAction.Delete -> viewModelScope.launch {
                runCatching { searchRepository.deleteSearch(action.id) }
            }
        }
    }

    private fun submit() {
        val text = query.value.trim()
        if (text.isEmpty() || starting.value) return
        viewModelScope.launch {
            starting.value = true
            runCatching { searchRepository.startSearch(text) }
                .onSuccess { id ->
                    query.value = ""
                    openChannel.send(id)
                }
            starting.value = false
        }
    }
}
