package com.slskdandroid.feature.browse.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.BrowseProgress
import com.slskdandroid.core.data.BrowseRepository
import com.slskdandroid.core.data.DownloadsRepository
import com.slskdandroid.core.model.BrowseDirectory
import com.slskdandroid.core.model.SearchResultFile
import com.slskdandroid.feature.browse.api.BROWSE_USER_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val browseRepository: BrowseRepository,
    private val downloadsRepository: DownloadsRepository,
) : ViewModel() {

    private val query = MutableStateFlow(savedStateHandle.get<String>(BROWSE_USER_ARG).orEmpty())
    private val state = MutableStateFlow<BrowseState>(BrowseState.Idle)
    private val view = MutableStateFlow(ViewState())

    private var browseJob: Job? = null

    val uiState: StateFlow<BrowseUiState> =
        combine(query, state, view) { q, s, v ->
            BrowseUiState(
                query = q,
                phase = derivePhase(s, v),
                selectedCount = v.selected.size,
                selectedSizeBytes = v.selected.values.sumOf { it },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BrowseUiState("", BrowsePhase.Idle, 0, 0),
        )

    init {
        if (query.value.isNotBlank()) submit()
    }

    fun onAction(action: BrowseAction) {
        when (action) {
            is BrowseAction.QueryChanged -> query.value = action.query
            BrowseAction.Submit -> submit()

            BrowseAction.CloseUser -> {
                browseJob?.cancel()
                state.value = BrowseState.Idle
                view.value = ViewState()
            }

            is BrowseAction.SetTreeFilter -> view.update { it.copy(treeFilter = action.text) }
            is BrowseAction.SetFileFilter -> view.update { it.copy(fileFilter = action.text) }

            is BrowseAction.ToggleExpand -> view.update {
                val next = if (action.path in it.expanded) it.expanded - action.path else it.expanded + action.path
                it.copy(expanded = next)
            }

            is BrowseAction.OpenDirectory -> view.update {
                it.copy(openDirectory = action.exactPath, selected = emptyMap(), fileFilter = "")
            }

            BrowseAction.CloseDirectory -> view.update {
                it.copy(openDirectory = null, selected = emptyMap(), fileFilter = "")
            }

            is BrowseAction.ToggleFileSelection -> view.update {
                val selected = it.selected.toMutableMap()
                if (action.file.filename in selected) {
                    selected.remove(action.file.filename)
                } else {
                    selected[action.file.filename] = action.file.sizeBytes
                }
                it.copy(selected = selected)
            }

            is BrowseAction.SetAllSelection -> view.update {
                val selected = it.selected.toMutableMap()
                action.files.forEach { file ->
                    if (action.selected) selected[file.filename] = file.sizeBytes else selected.remove(file.filename)
                }
                it.copy(selected = selected)
            }

            BrowseAction.ClearSelection -> view.update { it.copy(selected = emptyMap()) }

            BrowseAction.DownloadSelected -> {
                val username = (state.value as? BrowseState.Ready)?.username ?: return
                val files = view.value.selected.toMap()
                view.update { it.copy(selected = emptyMap()) }
                viewModelScope.launch {
                    files.forEach { (filename, size) ->
                        runCatching { downloadsRepository.enqueue(username, filename, size) }
                    }
                }
            }

            is BrowseAction.Download -> {
                val username = (state.value as? BrowseState.Ready)?.username ?: return
                viewModelScope.launch {
                    runCatching {
                        downloadsRepository.enqueue(username, action.file.filename, action.file.sizeBytes)
                    }
                }
            }
        }
    }

    private fun submit() {
        val username = query.value.trim()
        if (username.isEmpty() || state.value is BrowseState.Loading) return
        view.value = ViewState()
        state.value = BrowseState.Loading(username, percent = null)
        browseJob?.cancel()
        browseJob = browseRepository.browse(username)
            .onEach { progress ->
                state.value = when (progress) {
                    is BrowseProgress.Loading -> BrowseState.Loading(username, progress.percent)
                    is BrowseProgress.Loaded -> BrowseState.Ready(
                        username = username,
                        roots = buildTree(progress.directories),
                        filesByPath = progress.directories.associate { it.directory to it.files },
                    )
                }
            }
            .catch { state.value = BrowseState.Error(username, it.message ?: "Couldn't browse $username") }
            .launchIn(viewModelScope)
    }

    private fun derivePhase(state: BrowseState, view: ViewState): BrowsePhase = when (state) {
        BrowseState.Idle -> BrowsePhase.Idle
        is BrowseState.Loading -> BrowsePhase.Loading(state.username, state.percent)
        is BrowseState.Error -> BrowsePhase.Error(state.username, state.message)
        is BrowseState.Ready -> {
            val openDir = view.openDirectory
            if (openDir != null) {
                val files = (state.filesByPath[openDir] ?: emptyList())
                    .filter { view.fileFilter.isBlank() || it.displayName.contains(view.fileFilter, ignoreCase = true) }
                    .map { ShownFile(it, it.filename in view.selected) }
                BrowsePhase.Files(
                    username = state.username,
                    directory = openDir,
                    filter = view.fileFilter,
                    files = files,
                    selection = files.selectionState(),
                )
            } else {
                val rows = if (view.treeFilter.isBlank()) {
                    ArrayList<TreeRow>().also { flatten(state.roots, view.expanded, depth = 0, out = it) }
                } else {
                    // Flat list of folders whose full path matches; the path is the label.
                    state.filesByPath.entries
                        .filter { it.key.contains(view.treeFilter, ignoreCase = true) }
                        .sortedBy { it.key.lowercase() }
                        .map { (path, files) ->
                            TreeRow(
                                path = path,
                                exactPath = path,
                                name = path,
                                depth = 0,
                                fileCount = files.size,
                                hasChildren = false,
                                expanded = false,
                            )
                        }
                }
                BrowsePhase.Tree(state.username, view.treeFilter, rows)
            }
        }
    }

    private fun List<ShownFile>.selectionState(): TriState = when {
        isEmpty() || none { it.selected } -> TriState.None
        all { it.selected } -> TriState.All
        else -> TriState.Some
    }

    private sealed interface BrowseState {
        data object Idle : BrowseState
        data class Loading(val username: String, val percent: Int?) : BrowseState
        data class Error(val username: String, val message: String) : BrowseState
        data class Ready(
            val username: String,
            val roots: List<Node>,
            val filesByPath: Map<String, List<SearchResultFile>>,
        ) : BrowseState
    }

    private data class ViewState(
        val expanded: Set<String> = emptySet(),
        val openDirectory: String? = null,
        val selected: Map<String, Long> = emptyMap(),
        val treeFilter: String = "",
        val fileFilter: String = "",
    )
}

/** An immutable directory-tree node. [exactPath] is set only for folders that have their own files. */
internal class Node(
    val name: String,
    val path: String,
    val exactPath: String?,
    val fileCount: Int,
    val children: List<Node>,
)

/** Builds a directory tree from slskd's flat directory list by splitting paths into segments. */
internal fun buildTree(directories: List<BrowseDirectory>): List<Node> {
    val roots = LinkedHashMap<String, MutableNode>()
    for (dir in directories) {
        val separator = if (dir.directory.contains('\\')) '\\' else '/'
        val segments = dir.directory.split(separator).filter { it.isNotEmpty() }
        if (segments.isEmpty()) continue
        var level = roots
        var cumulative = ""
        var node: MutableNode? = null
        for (segment in segments) {
            cumulative = if (cumulative.isEmpty()) segment else "$cumulative$separator$segment"
            val current = level.getOrPut(segment) { MutableNode(segment, cumulative) }
            node = current
            level = current.children
        }
        node?.let { it.exactPath = dir.directory; it.fileCount = dir.files.size }
    }
    return roots.values.map { it.toNode() }
}

private class MutableNode(val name: String, val path: String) {
    val children = LinkedHashMap<String, MutableNode>()
    var exactPath: String? = null
    var fileCount: Int = 0

    fun toNode(): Node = Node(
        name = name,
        path = path,
        exactPath = exactPath,
        fileCount = fileCount,
        children = children.values
            .sortedBy { it.name.lowercase() }
            .map { it.toNode() },
    )
}

private fun flatten(nodes: List<Node>, expanded: Set<String>, depth: Int, out: MutableList<TreeRow>) {
    for (node in nodes) {
        val isExpanded = node.path in expanded
        out.add(
            TreeRow(
                path = node.path,
                exactPath = node.exactPath,
                name = node.name,
                depth = depth,
                fileCount = node.fileCount,
                hasChildren = node.children.isNotEmpty(),
                expanded = isExpanded,
            ),
        )
        if (isExpanded) flatten(node.children, expanded, depth + 1, out)
    }
}
