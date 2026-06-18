package com.slskdandroid.core.data

import com.slskdandroid.core.model.AvailableRoom
import com.slskdandroid.core.model.RoomMessage
import com.slskdandroid.core.model.RoomUser
import kotlinx.coroutines.flow.Flow

/**
 * Exposes slskd's chat rooms. As with DMs, there's no rooms SignalR hub, so the joined-room list
 * and an open room's messages are polled; the member list and the available-room search are
 * one-shot fetches.
 */
interface RoomsRepository {

    /** A live, polling stream of the names of the rooms the user has joined. */
    fun joinedRooms(): Flow<List<String>>

    /** A live, polling stream of the chat messages in a joined [room]. */
    fun messages(room: String): Flow<List<RoomMessage>>

    /** A one-shot snapshot of a joined room's current members. */
    suspend fun users(room: String): List<RoomUser>

    /** A one-shot snapshot of all rooms available on the network (the search list). */
    suspend fun availableRooms(): List<AvailableRoom>

    /** Joins [room]. */
    suspend fun join(room: String)

    /** Leaves [room]. */
    suspend fun leave(room: String)

    /** Sends [text] to [room]. */
    suspend fun send(room: String, text: String)
}
