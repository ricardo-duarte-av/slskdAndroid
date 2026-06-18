package com.slskdandroid.core.model

/**
 * A private-message conversation with a single peer, as surfaced by slskd's `conversations`
 * endpoint. The list view carries no message bodies — only the unread bookkeeping — so a preview
 * is not available without opening the thread.
 */
data class Conversation(
    val username: String,
    val isActive: Boolean,
    val unreadCount: Int,
    val hasUnread: Boolean,
)

/**
 * A single private message within a conversation. [isOutgoing] is true for messages this user sent
 * (slskd direction "Out"), false for messages received from the peer. [timestampMillis] is the
 * parsed epoch-millis of the server timestamp, or null if it couldn't be parsed.
 */
data class PrivateMessage(
    val id: Long,
    val username: String,
    val message: String,
    val isOutgoing: Boolean,
    val isAcknowledged: Boolean,
    val timestampMillis: Long?,
)
