package com.slskdandroid.core.network.model

import kotlinx.serialization.Serializable

/** Body for `POST /api/v0/users/{username}/directory` — requests a peer's listing of [directory]. */
@Serializable
data class DirectoryContentsRequest(
    val directory: String,
)

/**
 * One directory in a peer's browse/directory-contents response. [files] carry base filenames
 * (no directory prefix).
 */
@Serializable
data class NetworkDirectory(
    val name: String = "",
    val files: List<NetworkFile> = emptyList(),
)

/**
 * Wrapper returned by `GET /users/{username}/browse` (Soulseek's `BrowseResponse`): an object with
 * the share's directories. Note this differs from the directory-contents endpoint, which returns a
 * bare array.
 */
@Serializable
data class NetworkBrowseResponse(
    val directories: List<NetworkDirectory> = emptyList(),
    val lockedDirectories: List<NetworkDirectory> = emptyList(),
)
