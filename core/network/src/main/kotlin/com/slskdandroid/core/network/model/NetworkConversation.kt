package com.slskdandroid.core.network.model

import kotlinx.serialization.Serializable

/**
 * `GET /api/v0/conversations` (and `/{username}`) — a private-message thread with one peer.
 * [messages] is only populated when requested via `includeMessages=true`; the bare list endpoint
 * carries just the unread bookkeeping.
 */
@Serializable
data class NetworkConversation(
    val username: String = "",
    val isActive: Boolean = false,
    val unAcknowledgedMessageCount: Int = 0,
    val hasUnAcknowledgedMessages: Boolean = false,
    val messages: List<NetworkPrivateMessage>? = null,
)

/**
 * A single private message. [direction] is a serialized enum string (`In`/`Out`), [timestamp] an
 * ISO-8601 instant. [id] is slskd's per-conversation message id.
 */
@Serializable
data class NetworkPrivateMessage(
    val id: Long = 0,
    val timestamp: String? = null,
    val username: String = "",
    val direction: String? = null,
    val message: String = "",
    val isAcknowledged: Boolean = false,
    val wasReplayed: Boolean = false,
)
