package com.slskdandroid.core.network.model

import kotlinx.serialization.Serializable

/**
 * `GET /api/v0/users/{username}/info` — a peer's self-reported profile (Soulseek's `UserInfo`).
 * [picture] is a base64-encoded image, present only when [hasPicture] is true. [description] is
 * arbitrary free text the peer wrote about themselves.
 */
@Serializable
data class NetworkUserInfo(
    val description: String? = null,
    val hasFreeUploadSlot: Boolean = false,
    val hasPicture: Boolean = false,
    val picture: String? = null,
    val queueLength: Int = 0,
    val uploadSlots: Int = 0,
)

/**
 * `GET /api/v0/users/{username}/status`. [presence] is a serialized enum string
 * (`Online`/`Away`/`Offline`).
 */
@Serializable
data class NetworkUserStatus(
    val isPrivileged: Boolean = false,
    val presence: String? = null,
)

/**
 * `GET /api/v0/users/{username}/endpoint` — the peer's network address. slskd serializes the
 * .NET `IPEndPoint` to `{ address, port }`.
 */
@Serializable
data class NetworkUserEndpoint(
    val address: String? = null,
    val port: Int? = null,
)
