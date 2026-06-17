package com.slskdandroid.core.network.model

import kotlinx.serialization.Serializable

/** Body for `POST /api/v0/users/{username}/directory` — requests a peer's listing of [directory]. */
@Serializable
data class DirectoryContentsRequest(
    val directory: String,
)

/**
 * One directory in a peer's browse/directory-contents response. The endpoint returns a list of
 * these (the requested root first); [files] carry base filenames (no directory prefix).
 */
@Serializable
data class NetworkDirectory(
    val name: String = "",
    val files: List<NetworkFile> = emptyList(),
)
