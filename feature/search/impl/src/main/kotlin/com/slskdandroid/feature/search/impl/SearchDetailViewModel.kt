package com.slskdandroid.feature.search.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.DownloadsRepository
import com.slskdandroid.core.data.SearchProgress
import com.slskdandroid.core.data.SearchRepository
import com.slskdandroid.core.model.SearchResponse
import com.slskdandroid.core.model.SearchResultFile
import com.slskdandroid.feature.search.api.SEARCH_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 5

@HiltViewModel
class SearchDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
    private val downloadsRepository: DownloadsRepository,
) : ViewModel() {

    private val searchId: String = checkNotNull(savedStateHandle[SEARCH_ID_ARG])

    private val searchText = MutableStateFlow("")
    private val options = MutableStateFlow(SearchOptions.Default)
    private val interaction = MutableStateFlow(Interaction())

    // Streams (and reconciles) the search's responses over the hub only while the screen is
    // subscribed, so the SignalR connection isn't held open off-screen.
    private val baseFlow: Flow<BaseLoad> = searchRepository.observeSearch(searchId)
        .map<SearchProgress, BaseLoad> { BaseLoad.Loaded(it.responses, it.isComplete) }
        .catch { emit(BaseLoad.Error(it.message ?: "Couldn't load results")) }
        .onStart { emit(BaseLoad.Loading) }

    val uiState: StateFlow<SearchDetailUiState> =
        combine(searchText, baseFlow, options, interaction) { text, baseLoad, opts, inter ->
            SearchDetailUiState(
                searchText = text,
                options = opts,
                phase = derivePhase(baseLoad, opts, inter),
                selectedCount = inter.selected.size,
                selectedSizeBytes = inter.selected.values.sumOf { it.sizeBytes },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchDetailUiState("", SearchOptions.Default, Phase.Loading, 0, 0),
        )

    init {
        // Header text only — a single call, independent of the result stream.
        viewModelScope.launch {
            runCatching { searchRepository.getSearch(searchId) }
                .onSuccess { searchText.value = it.searchText }
        }
    }

    fun onAction(action: SearchDetailAction) {
        when (action) {
            is SearchDetailAction.SetSort -> options.update { it.copy(sort = action.sort) }
            SearchDetailAction.ToggleHideLocked -> options.update { it.copy(hideLocked = !it.hideLocked) }
            SearchDetailAction.ToggleHideNoFreeSlots ->
                options.update { it.copy(hideNoFreeSlots = !it.hideNoFreeSlots) }

            SearchDetailAction.ToggleFold -> {
                options.update { it.copy(foldResults = !it.foldResults) }
                interaction.update { it.copy(toggledPeers = emptySet()) }
            }

            is SearchDetailAction.SetFilter -> options.update { it.copy(filterText = action.text) }
            SearchDetailAction.ClearFilter -> options.update { it.copy(filterText = "") }

            is SearchDetailAction.TogglePeer -> interaction.update {
                val toggled = it.toggledPeers
                val next = if (action.username in toggled) toggled - action.username else toggled + action.username
                it.copy(toggledPeers = next)
            }

            is SearchDetailAction.ToggleDirectoryCollapse -> interaction.update {
                val key = dirKey(action.username, action.directory)
                val next = if (key in it.collapsedDirs) it.collapsedDirs - key else it.collapsedDirs + key
                it.copy(collapsedDirs = next)
            }

            SearchDetailAction.ShowMore -> interaction.update { it.copy(displayCount = it.displayCount + PAGE_SIZE) }

            is SearchDetailAction.ToggleFileSelection -> interaction.update {
                val key = fileKey(action.username, action.file.filename)
                val selected = it.selected.toMutableMap()
                if (key in selected) selected.remove(key) else selected[key] =
                    SelectedFile(action.username, action.file.filename, action.file.sizeBytes)
                it.copy(selected = selected)
            }

            is SearchDetailAction.SetDirectorySelection -> interaction.update {
                val selected = it.selected.toMutableMap()
                action.files.forEach { file ->
                    val key = fileKey(action.username, file.filename)
                    if (action.selected) {
                        selected[key] = SelectedFile(action.username, file.filename, file.sizeBytes)
                    } else {
                        selected.remove(key)
                    }
                }
                it.copy(selected = selected)
            }

            SearchDetailAction.ClearSelection -> interaction.update { it.copy(selected = emptyMap()) }

            SearchDetailAction.DownloadSelected -> {
                val toDownload = interaction.value.selected.values.toList()
                interaction.update { it.copy(selected = emptyMap()) }
                viewModelScope.launch {
                    toDownload.forEach { sel ->
                        runCatching { downloadsRepository.enqueue(sel.username, sel.filename, sel.sizeBytes) }
                    }
                }
            }

            is SearchDetailAction.ExpandDirectory -> expandDirectory(action.username, action.directory)

            is SearchDetailAction.Download -> viewModelScope.launch {
                runCatching {
                    downloadsRepository.enqueue(action.username, action.file.filename, action.file.sizeBytes)
                }
            }
        }
    }

    private fun expandDirectory(username: String, directory: String) {
        val key = dirKey(username, directory)
        if (interaction.value.expandingDirs.contains(key) || interaction.value.expandedDirs.containsKey(key)) {
            return
        }
        interaction.update { it.copy(expandingDirs = it.expandingDirs + key) }
        viewModelScope.launch {
            val files = runCatching { searchRepository.getDirectoryFiles(username, directory) }
                .getOrDefault(emptyList())
            interaction.update {
                it.copy(
                    expandingDirs = it.expandingDirs - key,
                    expandedDirs = it.expandedDirs + (key to files),
                )
            }
        }
    }

    private fun derivePhase(base: BaseLoad, options: SearchOptions, inter: Interaction): Phase =
        when (base) {
            BaseLoad.Loading -> Phase.Loading
            is BaseLoad.Error -> Phase.Error(base.message)
            is BaseLoad.Loaded -> {
                val filters = parseFilters(options.filterText)
                val matched = base.responses
                    .map { if (options.hideLocked) it.copy(lockedFiles = emptyList()) else it }
                    .map { it.applyFilters(filters) }
                    .filter { it.files.isNotEmpty() || it.lockedFiles.isNotEmpty() }
                    .filter { !(options.hideNoFreeSlots && !it.hasFreeUploadSlot) }
                    .sortedWith(comparatorFor(options.sort))

                val filteredCount = base.responses.size - matched.size
                val shown = matched.take(inter.displayCount).map { response ->
                    buildShownResponse(response, options, inter)
                }

                Phase.Loaded(
                    isComplete = base.isComplete,
                    responses = shown,
                    remainingCount = (matched.size - inter.displayCount).coerceAtLeast(0),
                    filteredCount = filteredCount,
                )
            }
        }

    private fun buildShownResponse(
        response: SearchResponse,
        options: SearchOptions,
        inter: Interaction,
    ): ShownResponse {
        val folded = options.foldResults != (response.username in inter.toggledPeers)
        val matchedFiles = response.files + response.lockedFiles
        val directories = matchedFiles.groupBy { it.directory }.map { (directory, files) ->
            val key = dirKey(response.username, directory)
            // An expanded directory shows its full contents (unfiltered), replacing the matches.
            val effectiveFiles = (inter.expandedDirs[key] ?: files)
                .sortedBy { it.displayName.lowercase() }
            val shownFiles = effectiveFiles.map { file ->
                ShownFile(file, selected = fileKey(response.username, file.filename) in inter.selected)
            }
            ShownDirectory(
                directory = directory,
                files = shownFiles,
                collapsed = key in inter.collapsedDirs,
                expanded = key in inter.expandedDirs,
                expanding = key in inter.expandingDirs,
                selection = shownFiles.selectionState(),
            )
        }
        return ShownResponse(
            username = response.username,
            hasFreeUploadSlot = response.hasFreeUploadSlot,
            uploadSpeed = response.uploadSpeed,
            queueLength = response.queueLength,
            fileCount = matchedFiles.size,
            folded = folded,
            directories = directories,
        )
    }

    private fun List<ShownFile>.selectionState(): TriState = when {
        isEmpty() || none { it.selected } -> TriState.None
        all { it.selected } -> TriState.All
        else -> TriState.Some
    }

    private sealed interface BaseLoad {
        data object Loading : BaseLoad
        data class Error(val message: String) : BaseLoad
        data class Loaded(val responses: List<SearchResponse>, val isComplete: Boolean) : BaseLoad
    }

    private data class Interaction(
        val displayCount: Int = PAGE_SIZE,
        val toggledPeers: Set<String> = emptySet(),
        val collapsedDirs: Set<String> = emptySet(),
        val selected: Map<String, SelectedFile> = emptyMap(),
        val expandingDirs: Set<String> = emptySet(),
        val expandedDirs: Map<String, List<SearchResultFile>> = emptyMap(),
    )
}

internal fun fileKey(username: String, filename: String): String = "$username\n$filename"
internal fun dirKey(username: String, directory: String): String = "$username\n$directory"
