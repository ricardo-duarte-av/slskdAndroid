package com.slskdandroid.feature.search.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.SearchRepository
import com.slskdandroid.core.model.Search
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    // Polls the searches list only while the screen is subscribed (see WhileSubscribed below).
    private val searches: Flow<SearchesState> = searchRepository.observeSearches()
        .map<List<Search>, SearchesState> { SearchesState.Loaded(it) }
        .catch { emit(SearchesState.Error(it.message ?: "Couldn't load searches")) }

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
