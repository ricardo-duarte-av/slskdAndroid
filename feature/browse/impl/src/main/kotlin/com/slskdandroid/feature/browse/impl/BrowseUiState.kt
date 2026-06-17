package com.slskdandroid.feature.browse.impl

import com.slskdandroid.core.model.SearchResultFile

data class BrowseUiState(
    /** The username field's current text (used on the Idle prompt). */
    val query: String,
    val phase: BrowsePhase,
    val selectedCount: Int,
    val selectedSizeBytes: Long,
)

sealed interface BrowsePhase {
    /** Nothing browsed yet — show the username prompt. */
    data object Idle : BrowsePhase

    /** [percent] is null while the figure is unknown (show an indeterminate indicator). */
    data class Loading(val username: String, val percent: Int?) : BrowsePhase

    data class Error(val username: String, val message: String) : BrowsePhase

    /** The directory tree for [username] (folders only); tap a folder to view its files. */
    data class Tree(val username: String, val filter: String, val rows: List<TreeRow>) : BrowsePhase

    /** The files of one opened directory. */
    data class Files(
        val username: String,
        val directory: String,
        val filter: String,
        val files: List<ShownFile>,
        val selection: TriState,
    ) : BrowsePhase
}

/** A flattened, visible node of the directory tree. */
data class TreeRow(
    /** Stable key for expand state (reconstructed path). */
    val path: String,
    /** Exact slskd directory path if this folder has its own files, else null. */
    val exactPath: String?,
    val name: String,
    val depth: Int,
    val fileCount: Int,
    val hasChildren: Boolean,
    val expanded: Boolean,
)

data class ShownFile(
    val file: SearchResultFile,
    val selected: Boolean,
)

enum class TriState { None, Some, All }

sealed interface BrowseAction {
    data class QueryChanged(val query: String) : BrowseAction
    data object Submit : BrowseAction

    /** Stop browsing the current user and return to the prompt. */
    data object CloseUser : BrowseAction

    /** Filters the directory tree to folders whose path matches (shown flat while non-empty). */
    data class SetTreeFilter(val text: String) : BrowseAction

    /** Filters the open folder's files by name. */
    data class SetFileFilter(val text: String) : BrowseAction

    data class ToggleExpand(val path: String) : BrowseAction

    /** Open a folder's file list (only for folders with files). */
    data class OpenDirectory(val exactPath: String) : BrowseAction

    /** Return from the file list to the directory tree. */
    data object CloseDirectory : BrowseAction

    data class ToggleFileSelection(val file: SearchResultFile) : BrowseAction
    data class SetAllSelection(val files: List<SearchResultFile>, val selected: Boolean) : BrowseAction
    data object DownloadSelected : BrowseAction
    data object ClearSelection : BrowseAction
    data class Download(val file: SearchResultFile) : BrowseAction
}
