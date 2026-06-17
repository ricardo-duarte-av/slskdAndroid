package com.slskdandroid.core.model

/** A single file returned by a Soulseek search, as surfaced to the UI layer. */
data class SearchResultFile(
    val filename: String,
    val sizeBytes: Long,
    val bitRate: Int?,
    val lengthSeconds: Int?,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val isVariableBitRate: Boolean?,
    val extension: String?,
    /** True if the peer is sharing this file under a locked/private folder. */
    val isLocked: Boolean,
) {
    /** The file's base name, split off the remote path on either separator. */
    val displayName: String
        get() = filename.substringAfterLast('\\').substringAfterLast('/')

    /** The remote directory portion of [filename] (everything before the base name). */
    val directory: String
        get() = filename.substringBeforeLast('\\', filename.substringBeforeLast('/', ""))
}

/** Files offered by one peer in response to a search. */
data class SearchResponse(
    val username: String,
    val hasFreeUploadSlot: Boolean,
    val uploadSpeed: Long,
    val queueLength: Long,
    /** Freely downloadable files. */
    val files: List<SearchResultFile>,
    /** Files behind a locked/private folder; downloadable only if the peer grants access. */
    val lockedFiles: List<SearchResultFile>,
)

/** A search and its current state on the slskd server (the list-view projection, no responses). */
data class Search(
    val id: String,
    val searchText: String,
    /** Raw slskd `SearchStates` flags string, e.g. "Completed, ResponseLimitReached" or "InProgress". */
    val state: String,
    val isComplete: Boolean,
    val fileCount: Int,
    val lockedFileCount: Int,
    val responseCount: Int,
    /** ISO-8601 timestamps as reported by slskd, or null. */
    val startedAt: String?,
    val endedAt: String?,
)
