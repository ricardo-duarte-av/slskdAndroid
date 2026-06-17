package com.slskdandroid.core.model

/**
 * Coarse lifecycle of an upload, distilled from slskd's `TransferStates` flags enum (same shape as
 * a download's). The raw string is preserved on [Upload.rawState]. Note there is no upload retry —
 * uploads are initiated by the remote peer requesting a file you share.
 */
enum class UploadState {
    Queued,
    InProgress,
    Completed,
    Failed,
    Unknown,
}

/** A single file upload as surfaced to the UI, flattened from slskd's user/directory grouping. */
data class Upload(
    val id: String,
    val username: String,
    /** The remote directory the file lives in, as grouped by slskd. */
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
    val state: UploadState,
    val rawState: String,
    val exception: String?,
) {
    /** The file's base name, split off the remote path on either separator. */
    val displayName: String
        get() = filename.substringAfterLast('\\').substringAfterLast('/')
}
