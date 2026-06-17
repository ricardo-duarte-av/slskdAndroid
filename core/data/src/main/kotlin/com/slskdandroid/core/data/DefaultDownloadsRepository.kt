package com.slskdandroid.core.data

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.Download
import com.slskdandroid.core.model.DownloadState
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.model.NetworkTransfer
import com.slskdandroid.core.network.model.QueueDownloadRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class DefaultDownloadsRepository @Inject constructor(
    private val api: SlskdApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DownloadsRepository {

    override fun downloads(): Flow<List<Download>> = flow {
        // No transfers push hub exists, so poll. The first fetch may fail loudly (so the UI can
        // surface a connection error); once we've emitted at least once, transient blips are
        // swallowed to keep the stream — and thus the list — alive.
        var emittedOnce = false
        while (currentCoroutineContext().isActive) {
            runCatching { fetchDownloads() }
                .onSuccess { emittedOnce = true; emit(it) }
                .onFailure { if (!emittedOnce) throw it }
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(ioDispatcher)

    private suspend fun fetchDownloads(): List<Download> =
        api.getDownloads()
            .flatMap { user ->
                user.directories.flatMap { dir -> dir.files.map { it.toModel(dir.directory) } }
            }

    override suspend fun cancel(username: String, id: String, remove: Boolean) {
        withContext(ioDispatcher) { api.cancelDownload(username, id, remove) }
    }

    override suspend fun retry(download: Download) {
        enqueue(download.username, download.filename, download.sizeBytes)
    }

    override suspend fun enqueue(username: String, filename: String, sizeBytes: Long) {
        withContext(ioDispatcher) {
            api.enqueueDownloads(username, listOf(QueueDownloadRequest(filename, sizeBytes)))
        }
    }
}

private fun NetworkTransfer.toModel(directory: String) = Download(
    id = id,
    username = username,
    directory = directory,
    filename = filename,
    sizeBytes = size,
    bytesTransferred = bytesTransferred,
    averageSpeed = averageSpeed,
    percentComplete = percentComplete,
    placeInQueue = placeInQueue,
    state = state.toDownloadState(),
    rawState = state,
    exception = exception,
)

/**
 * Distills slskd's `TransferStates` flags string into a coarse [DownloadState]. Order matters:
 * a successful transfer reads "Completed, Succeeded", a failed one "Completed, Errored", etc., so
 * the terminal outcomes are checked before the in-progress/queued flags.
 */
private fun String.toDownloadState(): DownloadState = when {
    contains("Succeeded", ignoreCase = true) -> DownloadState.Completed
    listOf("Cancelled", "Errored", "TimedOut", "Rejected", "Aborted")
        .any { contains(it, ignoreCase = true) } -> DownloadState.Failed
    contains("InProgress", ignoreCase = true) ||
        contains("Initializing", ignoreCase = true) -> DownloadState.InProgress
    contains("Queued", ignoreCase = true) ||
        contains("Requested", ignoreCase = true) -> DownloadState.Queued
    else -> DownloadState.Unknown
}

private const val POLL_INTERVAL_MS = 1_000L
