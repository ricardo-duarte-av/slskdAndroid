package com.slskdandroid.core.network.model

import kotlinx.serialization.Serializable

/**
 * slskd Transfers controller DTOs, shared by both directions. `GET /api/v0/transfers/downloads`
 * and `.../uploads` return the same shape: transfers grouped by user, then by directory; the leaf
 * [NetworkTransfer] objects carry per-file state/progress. Field names mirror the slskd OpenAPI
 * schema — verify against the live Swagger before relying on them. See the project CLAUDE.md.
 */

@Serializable
data class NetworkUserDownloads(
    val username: String = "",
    val directories: List<NetworkDownloadDirectory> = emptyList(),
)

@Serializable
data class NetworkDownloadDirectory(
    val directory: String = "",
    val fileCount: Int = 0,
    val files: List<NetworkTransfer> = emptyList(),
)

/** One file to (re-)enqueue via `POST /api/v0/transfers/downloads/{username}`. Used for retries. */
@Serializable
data class QueueDownloadRequest(
    val filename: String,
    val size: Long,
)

@Serializable
data class NetworkTransfer(
    val id: String,
    val username: String = "",
    val direction: String = "",
    val filename: String = "",
    val size: Long = 0,
    // A serialized TransferStates flags enum, e.g. "InProgress" or "Completed, Succeeded".
    val state: String = "",
    val bytesTransferred: Long = 0,
    val averageSpeed: Double = 0.0,
    val percentComplete: Double = 0.0,
    val placeInQueue: Int? = null,
    val exception: String? = null,
)
