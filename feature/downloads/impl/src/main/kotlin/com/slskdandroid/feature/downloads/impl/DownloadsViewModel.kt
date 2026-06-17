package com.slskdandroid.feature.downloads.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.DownloadsRepository
import com.slskdandroid.core.model.Download
import com.slskdandroid.core.model.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadsRepository: DownloadsRepository,
) : ViewModel() {

    // Latest list, cached for action handlers. Updated only while the screen is subscribed (the
    // polling flow below is part of the uiState chain), so nothing polls off-screen.
    private val downloads = MutableStateFlow<List<Download>>(emptyList())
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val collapsedUsers = MutableStateFlow<Set<String>>(emptySet())
    private val collapsedDirectories = MutableStateFlow<Set<String>>(emptySet())

    private val loadResult: Flow<LoadResult> = downloadsRepository.downloads()
        .onEach { downloads.value = it }
        .map<List<Download>, LoadResult> { LoadResult.Data(it) }
        .catch { emit(LoadResult.Failure(it.message ?: "Couldn't load downloads")) }

    val uiState: StateFlow<DownloadsUiState> =
        combine(
            loadResult,
            selectedIds,
            collapsedUsers,
            collapsedDirectories,
        ) { result, selected, collapsedU, collapsedD ->
            when (result) {
                is LoadResult.Data -> {
                    // Drop selections for downloads that have since vanished from the list.
                    val present = selected.intersect(result.downloads.mapTo(HashSet()) { it.id })
                    DownloadsUiState(LoadState.Loaded, result.downloads.groupByUser(), present, collapsedU, collapsedD)
                }

                is LoadResult.Failure ->
                    DownloadsUiState(LoadState.Error(result.message), emptyList(), selected, collapsedU, collapsedD)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DownloadsUiState.Initial,
        )

    private sealed interface LoadResult {
        data class Data(val downloads: List<Download>) : LoadResult
        data class Failure(val message: String) : LoadResult
    }

    fun onAction(action: DownloadsAction) {
        when (action) {
            is DownloadsAction.ToggleCollapse -> collapsedUsers.update {
                if (action.username in it) it - action.username else it + action.username
            }

            is DownloadsAction.ToggleDirectoryCollapse -> {
                val key = directoryKey(action.username, action.directory)
                collapsedDirectories.update { if (key in it) it - key else it + key }
            }

            is DownloadsAction.StartSelection -> selectedIds.update { it + action.id }
            is DownloadsAction.ToggleSelection -> selectedIds.update {
                if (action.id in it) it - action.id else it + action.id
            }

            DownloadsAction.ClearSelection -> selectedIds.value = emptySet()

            DownloadsAction.CancelSelected -> {
                runBulk(selectedDownloads()) { cancel(it.username, it.id, remove = false) }
                selectedIds.value = emptySet()
            }

            DownloadsAction.RemoveSelected -> {
                runBulk(selectedDownloads()) { cancel(it.username, it.id, remove = true) }
                selectedIds.value = emptySet()
            }

            is DownloadsAction.BulkRetry ->
                runBulk(downloads.value.filter { it.matchesRetry(action.filter) }) { retry(it) }

            is DownloadsAction.BulkCancel ->
                runBulk(downloads.value.filter { it.matchesCancel(action.filter) }) {
                    cancel(it.username, it.id, remove = false)
                }

            is DownloadsAction.BulkRemove ->
                runBulk(downloads.value.filter { it.matchesRemove(action.filter) }) {
                    cancel(it.username, it.id, remove = true)
                }
        }
    }

    private fun selectedDownloads(): List<Download> {
        val ids = selectedIds.value
        return downloads.value.filter { it.id in ids }
    }

    /** Fires [op] for each target in parallel; failures are swallowed (the next poll reconciles). */
    private fun runBulk(targets: List<Download>, op: suspend DownloadsRepository.(Download) -> Unit) {
        if (targets.isEmpty()) return
        viewModelScope.launch {
            coroutineScope {
                targets.forEach { download ->
                    launch { runCatching { downloadsRepository.op(download) } }
                }
            }
        }
    }
}

/**
 * Groups a flat download list by peer, then by remote directory, preserving first-seen order at
 * both levels.
 */
private fun List<Download>.groupByUser(): List<UserDownloads> =
    groupBy { it.username }.map { (username, forUser) ->
        val directories = forUser.groupBy { it.directory }
            .map { (directory, files) -> DirectoryDownloads(directory, files) }
        UserDownloads(username, fileCount = forUser.size, directories = directories)
    }

// Failure flavours are distinguished by the raw slskd state string, since the model collapses
// them all into DownloadState.Failed.
private val ERROR_FLAGS = listOf("Errored", "TimedOut", "Rejected", "Aborted")

private fun Download.isCancelled() = rawState.contains("Cancelled", ignoreCase = true)
private fun Download.isErrored() = ERROR_FLAGS.any { rawState.contains(it, ignoreCase = true) }

private fun Download.matchesRetry(filter: RetryFilter): Boolean = state == DownloadState.Failed && when (filter) {
    RetryFilter.Errored -> isErrored()
    RetryFilter.Cancelled -> isCancelled()
    RetryFilter.All -> true
}

private fun Download.matchesCancel(filter: CancelFilter): Boolean = when (filter) {
    CancelFilter.All -> state == DownloadState.Queued || state == DownloadState.InProgress
    CancelFilter.Queued -> state == DownloadState.Queued
    CancelFilter.InProgress -> state == DownloadState.InProgress
}

private fun Download.matchesRemove(filter: RemoveFilter): Boolean = when (filter) {
    RemoveFilter.Succeeded -> state == DownloadState.Completed
    RemoveFilter.Errored -> state == DownloadState.Failed && isErrored()
    RemoveFilter.Cancelled -> state == DownloadState.Failed && isCancelled()
    RemoveFilter.Completed -> state == DownloadState.Completed || state == DownloadState.Failed
}
