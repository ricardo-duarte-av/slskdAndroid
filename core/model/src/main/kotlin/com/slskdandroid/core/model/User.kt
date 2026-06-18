package com.slskdandroid.core.model

/**
 * A peer's profile, aggregated from slskd's `info`, `status`, and `endpoint` endpoints.
 * [ipAddress]/[port] and [presence] come from the status/endpoint lookups, which may be unavailable
 * (e.g. the peer is offline) — hence nullable / [UserPresence.Unknown]. [description] is arbitrary
 * free text and [pictureBase64] an optional self-portrait.
 */
data class UserProfile(
    val username: String,
    val presence: UserPresence,
    val isPrivileged: Boolean,
    val hasFreeUploadSlot: Boolean,
    val uploadSlots: Int,
    val queueLength: Int,
    val ipAddress: String?,
    val port: Int?,
    val description: String?,
    val pictureBase64: String?,
)

/** A peer's online presence. [Unknown] when the status lookup failed/was unavailable. */
enum class UserPresence { Online, Away, Offline, Unknown }
