package com.slskdandroid.core.model

/** A single file returned by a Soulseek search, as surfaced to the UI layer. */
data class SearchResultFile(
    val filename: String,
    val sizeBytes: Long,
    val bitRate: Int?,
    val lengthSeconds: Int?,
)

/** Files offered by one peer in response to a search. */
data class SearchResponse(
    val username: String,
    val hasFreeUploadSlot: Boolean,
    val uploadSpeed: Long,
    val queueLength: Long,
    val files: List<SearchResultFile>,
)

/** A search and its current state on the slskd server. */
data class Search(
    val id: String,
    val searchText: String,
    val isComplete: Boolean,
    val responseCount: Int,
    val fileCount: Int,
)
