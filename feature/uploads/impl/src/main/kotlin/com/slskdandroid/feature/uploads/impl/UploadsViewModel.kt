package com.slskdandroid.feature.uploads.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.UploadsRepository
import com.slskdandroid.core.model.Upload
import com.slskdandroid.core.model.UploadState
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
class UploadsViewModel @Inject constructor(
    private val uploadsRepository: UploadsRepository,
) : ViewModel() {

    // Latest list, cached for action handlers. Updated only while the screen is subscribed (the
    // polling flow below is part of the uiState chain), so nothing polls off-screen.
    private val uploads = MutableStateFlow<List<Upload>>(emptyList())
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val collapsedUsers = MutableStateFlow<Set<String>>(emptySet())
    private val collapsedDirectories = MutableStateFlow<Set<String>>(emptySet())

    private val loadResult: Flow<LoadResult> = uploadsRepository.uploads()
        .onEach { uploads.value = it }
        .map<List<Upload>, LoadResult> { LoadResult.Data(it) }
        .catch { emit(LoadResult.Failure(it.message ?: "Couldn't load uploads")) }

    val uiState: StateFlow<UploadsUiState> =
        combine(
            loadResult,
            selectedIds,
            collapsedUsers,
            collapsedDirectories,
        ) { result, selected, collapsedU, collapsedD ->
            when (result) {
                is LoadResult.Data -> {
                    // Drop selections for uploads that have since vanished from the list.
                    val present = selected.intersect(result.uploads.mapTo(HashSet()) { it.id })
                    UploadsUiState(LoadState.Loaded, result.uploads.groupByUser(), present, collapsedU, collapsedD)
                }

                is LoadResult.Failure ->
                    UploadsUiState(LoadState.Error(result.message), emptyList(), selected, collapsedU, collapsedD)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UploadsUiState.Initial,
        )

    private sealed interface LoadResult {
        data class Data(val uploads: List<Upload>) : LoadResult
        data class Failure(val message: String) : LoadResult
    }

    fun onAction(action: UploadsAction) {
        when (action) {
            is UploadsAction.ToggleCollapse -> collapsedUsers.update {
                if (action.username in it) it - action.username else it + action.username
            }

            is UploadsAction.ToggleDirectoryCollapse -> {
                val key = directoryKey(action.username, action.directory)
                collapsedDirectories.update { if (key in it) it - key else it + key }
            }

            is UploadsAction.StartSelection -> selectedIds.update { it + action.id }
            is UploadsAction.ToggleSelection -> selectedIds.update {
                if (action.id in it) it - action.id else it + action.id
            }

            UploadsAction.ClearSelection -> selectedIds.value = emptySet()

            UploadsAction.RemoveSelected -> {
                val ids = selectedIds.value
                runBulk(uploads.value.filter { it.id in ids }) {
                    cancel(it.username, it.id, remove = true)
                }
                selectedIds.value = emptySet()
            }

            is UploadsAction.BulkCancel ->
                runBulk(uploads.value.filter { it.matchesCancel(action.filter) }) {
                    cancel(it.username, it.id, remove = false)
                }

            is UploadsAction.BulkRemove ->
                runBulk(uploads.value.filter { it.matchesRemove(action.filter) }) {
                    cancel(it.username, it.id, remove = true)
                }
        }
    }

    /** Fires [op] for each target in parallel; failures are swallowed (the next poll reconciles). */
    private fun runBulk(targets: List<Upload>, op: suspend UploadsRepository.(Upload) -> Unit) {
        if (targets.isEmpty()) return
        viewModelScope.launch {
            coroutineScope {
                targets.forEach { upload ->
                    launch { runCatching { uploadsRepository.op(upload) } }
                }
            }
        }
    }
}

/** Groups a flat upload list by peer, then by remote directory, preserving first-seen order. */
private fun List<Upload>.groupByUser(): List<UserUploads> =
    groupBy { it.username }.map { (username, forUser) ->
        val directories = forUser.groupBy { it.directory }
            .map { (directory, files) -> DirectoryUploads(directory, files) }
        UserUploads(username, fileCount = forUser.size, directories = directories)
    }

private val ERROR_FLAGS = listOf("Errored", "TimedOut", "Rejected", "Aborted")

private fun Upload.isCancelled() = rawState.contains("Cancelled", ignoreCase = true)
private fun Upload.isErrored() = ERROR_FLAGS.any { rawState.contains(it, ignoreCase = true) }

private fun Upload.matchesCancel(filter: CancelFilter): Boolean = when (filter) {
    CancelFilter.All -> state == UploadState.Queued || state == UploadState.InProgress
    CancelFilter.Queued -> state == UploadState.Queued
    CancelFilter.InProgress -> state == UploadState.InProgress
}

private fun Upload.matchesRemove(filter: RemoveFilter): Boolean = when (filter) {
    RemoveFilter.Succeeded -> state == UploadState.Completed
    RemoveFilter.Errored -> state == UploadState.Failed && isErrored()
    RemoveFilter.Cancelled -> state == UploadState.Failed && isCancelled()
    RemoveFilter.Completed -> state == UploadState.Completed || state == UploadState.Failed
}
