package com.slskdandroid.core.network.model

import kotlinx.serialization.Serializable

/** `GET /api/v0/rooms/available` — a room on the network and its occupancy/access flags. */
@Serializable
data class NetworkAvailableRoom(
    val name: String = "",
    val userCount: Int = 0,
    val isPrivate: Boolean = false,
    val isOwned: Boolean = false,
    val isModerated: Boolean = false,
)

/** `GET /api/v0/rooms/joined/{room}/messages` — a room chat message. [self] flags own messages. */
@Serializable
data class NetworkRoomMessage(
    val timestamp: String? = null,
    val username: String = "",
    val message: String = "",
    val roomName: String? = null,
    val self: Boolean? = null,
)

/** `GET /api/v0/rooms/joined/{room}/users` — a member of a joined room. */
@Serializable
data class NetworkRoomUser(
    val username: String = "",
    val countryCode: String? = null,
    val status: String? = null,
    val fileCount: Int = 0,
    val directoryCount: Int = 0,
    val averageSpeed: Long = 0,
    val slotsFree: Int = 0,
    val uploadCount: Int = 0,
)
