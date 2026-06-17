package com.slskdandroid.feature.search.impl

import com.slskdandroid.core.model.SearchResultFile

data class SearchDetailUiState(
    val searchText: String,
    val options: SearchOptions,
    val phase: Phase,
    /** Across-search selection summary, for the bottom "Download selected" bar. */
    val selectedCount: Int,
    val selectedSizeBytes: Long,
)

/** User-controlled view options (sort, switches, filter text). */
data class SearchOptions(
    val sort: ResultSort = ResultSort.UploadSpeed,
    val hideLocked: Boolean = true,
    val hideNoFreeSlots: Boolean = false,
    val foldResults: Boolean = false,
    val filterText: String = "",
) {
    companion object {
        val Default = SearchOptions()
    }
}

sealed interface Phase {
    data object Loading : Phase

    data class Error(val message: String) : Phase

    data class Loaded(
        val isComplete: Boolean,
        val responses: List<ShownResponse>,
        val remainingCount: Int,
        val filteredCount: Int,
    ) : Phase
}

/** A peer's response, ready to render: header info + its directories (grouped). */
data class ShownResponse(
    val username: String,
    val hasFreeUploadSlot: Boolean,
    val uploadSpeed: Long,
    val queueLength: Long,
    val fileCount: Int,
    val folded: Boolean,
    val directories: List<ShownDirectory>,
)

data class ShownDirectory(
    val directory: String,
    val files: List<ShownFile>,
    /** Whether the file list is collapsed (hidden) in the UI. */
    val collapsed: Boolean,
    /** Whether this folder's full contents have been fetched ("search additional files"). */
    val expanded: Boolean,
    /** Whether that fetch is currently in flight. */
    val expanding: Boolean,
    /** Aggregate selection state of [files]; drives the long-press select/deselect-all. */
    val selection: TriState,
)

data class ShownFile(
    val file: SearchResultFile,
    val selected: Boolean,
)

/** Aggregate selection state of a group. */
enum class TriState { None, Some, All }

/** Identifies a selected file across the whole search (peer + absolute path). */
data class SelectedFile(
    val username: String,
    val filename: String,
    val sizeBytes: Long,
)

sealed interface SearchDetailAction {
    data class SetSort(val sort: ResultSort) : SearchDetailAction
    data object ToggleHideLocked : SearchDetailAction
    data object ToggleHideNoFreeSlots : SearchDetailAction
    data object ToggleFold : SearchDetailAction
    data class SetFilter(val text: String) : SearchDetailAction
    data object ClearFilter : SearchDetailAction

    /** Expand/collapse a single peer (overrides the global fold default). */
    data class TogglePeer(val username: String) : SearchDetailAction

    /** Collapse/expand a single directory's file list (tap on its sub-header). */
    data class ToggleDirectoryCollapse(val username: String, val directory: String) : SearchDetailAction

    data object ShowMore : SearchDetailAction

    /** Fetch a folder's full contents from the peer. */
    data class ExpandDirectory(val username: String, val directory: String) : SearchDetailAction

    /** Toggle selection of one file. */
    data class ToggleFileSelection(val username: String, val file: SearchResultFile) : SearchDetailAction

    /** Select or clear all of [files] in a directory at once. */
    data class SetDirectorySelection(
        val username: String,
        val files: List<SearchResultFile>,
        val selected: Boolean,
    ) : SearchDetailAction

    /** Enqueue downloads for all currently-selected files. */
    data object DownloadSelected : SearchDetailAction

    data object ClearSelection : SearchDetailAction

    /** Quick single-file download (the per-row download icon). */
    data class Download(val username: String, val file: SearchResultFile) : SearchDetailAction
}
