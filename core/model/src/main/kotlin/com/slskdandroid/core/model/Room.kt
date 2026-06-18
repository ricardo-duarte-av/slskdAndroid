package com.slskdandroid.core.model

/**
 * A chat room available on the network (`rooms/available`). [userCount] is the current occupancy;
 * [isPrivate]/[isOwned]/[isModerated] reflect the room's access flags.
 */
data class AvailableRoom(
    val name: String,
    val userCount: Int,
    val isPrivate: Boolean,
    val isOwned: Boolean,
    val isModerated: Boolean,
)

/**
 * A message in a joined room. [isSelf] is true for messages this user sent. Unlike DMs, rooms are
 * multi-party, so [username] (the sender) is rendered on each message.
 */
data class RoomMessage(
    val username: String,
    val message: String,
    val isSelf: Boolean,
    val timestampMillis: Long?,
)

/**
 * A member of a joined room (`rooms/joined/{room}/users`). [countryCode] is an ISO 3166-1 alpha-2
 * code (e.g. "FR"), rendered as a flag emoji; null when the peer reports none.
 */
data class RoomUser(
    val username: String,
    val countryCode: String?,
    val status: String?,
    val fileCount: Int,
    val averageSpeed: Long,
)
