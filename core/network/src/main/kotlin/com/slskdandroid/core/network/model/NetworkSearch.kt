package com.slskdandroid.core.network.model

import kotlinx.serialization.Serializable

/**
 * slskd network DTOs. Shapes mirror the slskd OpenAPI schema (Searches controller).
 * Verify field names against the live Swagger of the target slskd version before relying
 * on them — they shift between releases. See the project CLAUDE.md.
 */

@Serializable
data class StartSearchRequest(
    val searchText: String,
    val id: String? = null,
)

@Serializable
data class NetworkSearch(
    val id: String,
    val searchText: String,
    val isComplete: Boolean = false,
    val responseCount: Int = 0,
    val fileCount: Int = 0,
    // `state` (a SearchStates flags enum) is intentionally omitted — its JSON form varies and
    // completion is read from `isComplete`. Unknown keys are ignored by the Json config.
)

@Serializable
data class NetworkSearchResponse(
    val username: String,
    val hasFreeUploadSlot: Boolean = false,
    val uploadSpeed: Long = 0,
    val queueLength: Long = 0,
    val files: List<NetworkFile> = emptyList(),
)

@Serializable
data class NetworkFile(
    val filename: String,
    val size: Long = 0,
    val bitRate: Int? = null,
    val length: Int? = null,
)
