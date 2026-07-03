package com.slskdandroid.core.network.model

import kotlinx.serialization.Serializable

/**
 * `GET /api/v0/application` — the slskd instance's state. Only the current user's identity is
 * modelled here (used to detect room mentions of our own username); the rest is ignored.
 */
@Serializable
data class NetworkApplicationInfo(
    val user: NetworkApplicationUser? = null,
)

@Serializable
data class NetworkApplicationUser(
    val username: String? = null,
)
