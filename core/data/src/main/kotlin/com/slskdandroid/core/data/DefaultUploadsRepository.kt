package com.slskdandroid.core.data

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.Upload
import com.slskdandroid.core.model.UploadState
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.model.NetworkTransfer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class DefaultUploadsRepository @Inject constructor(
    private val api: SlskdApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UploadsRepository {

    override fun uploads(): Flow<List<Upload>> = flow {
        // Mirror downloads: poll, fail loudly on the first fetch, then swallow transient blips.
        var emittedOnce = false
        while (currentCoroutineContext().isActive) {
            runCatching { fetchUploads() }
                .onSuccess { emittedOnce = true; emit(it) }
                .onFailure { if (!emittedOnce) throw it }
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(ioDispatcher)

    private suspend fun fetchUploads(): List<Upload> =
        api.getUploads()
            .flatMap { user ->
                user.directories.flatMap { dir -> dir.files.map { it.toModel(dir.directory) } }
            }

    override suspend fun cancel(username: String, id: String, remove: Boolean) {
        withContext(ioDispatcher) { api.cancelUpload(username, id, remove) }
    }
}

private fun NetworkTransfer.toModel(directory: String) = Upload(
    id = id,
    username = username,
    directory = directory,
    filename = filename,
    sizeBytes = size,
    bytesTransferred = bytesTransferred,
    averageSpeed = averageSpeed,
    percentComplete = percentComplete,
    placeInQueue = placeInQueue,
    state = state.toUploadState(),
    rawState = state,
    exception = exception,
)

/** Distills slskd's `TransferStates` flags string into a coarse [UploadState] (see download equivalent). */
private fun String.toUploadState(): UploadState = when {
    contains("Succeeded", ignoreCase = true) -> UploadState.Completed
    listOf("Cancelled", "Errored", "TimedOut", "Rejected", "Aborted")
        .any { contains(it, ignoreCase = true) } -> UploadState.Failed
    contains("InProgress", ignoreCase = true) ||
        contains("Initializing", ignoreCase = true) -> UploadState.InProgress
    contains("Queued", ignoreCase = true) ||
        contains("Requested", ignoreCase = true) -> UploadState.Queued
    else -> UploadState.Unknown
}

private const val POLL_INTERVAL_MS = 1_000L
