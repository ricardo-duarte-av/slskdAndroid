package com.slskdandroid.core.model

/**
 * Coarse lifecycle of a download, distilled from slskd's `TransferStates` flags enum
 * (e.g. "Queued, Remotely", "InProgress", "Completed, Succeeded", "Completed, Errored").
 * The raw string is preserved on [Download.rawState] for display/debugging.
 */
enum class DownloadState {
    Queued,
    InProgress,
    Completed,
    Failed,
    Unknown,
}

/** A single file download as surfaced to the UI, flattened from slskd's user/directory grouping. */
data class Download(
    val id: String,
    val username: String,
    /** The remote directory this file lives in on the peer, as grouped by slskd. */
    val directory: String,
    /** Remote path exactly as slskd reports it; use [displayName] for the file's base name. */
    val filename: String,
    val sizeBytes: Long,
    val bytesTransferred: Long,
    /** Average speed over the transfer's duration, in bytes/second. */
    val averageSpeed: Double,
    /** 0..100, as computed by slskd. */
    val percentComplete: Double,
    val placeInQueue: Int?,
    val state: DownloadState,
    val rawState: String,
    val exception: String?,
) {
    /** The file's base name, split off the remote path on either separator. */
    val displayName: String
        get() = filename.substringAfterLast('\\').substringAfterLast('/')
}
