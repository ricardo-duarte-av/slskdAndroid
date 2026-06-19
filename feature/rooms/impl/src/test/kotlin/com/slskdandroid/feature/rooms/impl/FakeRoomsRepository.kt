package com.slskdandroid.feature.rooms.impl

import com.slskdandroid.core.data.RoomsRepository
import com.slskdandroid.core.model.AvailableRoom
import com.slskdandroid.core.model.RoomMessage
import com.slskdandroid.core.model.RoomUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Test double for [RoomsRepository]; streams are configurable and actions are recorded. */
class FakeRoomsRepository : RoomsRepository {

    var joinedRoomsFlow: Flow<List<String>> = flowOf(emptyList())
    var messagesFlow: (String) -> Flow<List<RoomMessage>> = { flowOf(emptyList()) }
    var usersResult: List<RoomUser> = emptyList()
    var availableRoomsResult: List<AvailableRoom> = emptyList()

    val joined = mutableListOf<String>()
    val left = mutableListOf<String>()
    val sent = mutableListOf<Pair<String, String>>()

    override fun joinedRooms(): Flow<List<String>> = joinedRoomsFlow
    override fun messages(room: String): Flow<List<RoomMessage>> = messagesFlow(room)
    override suspend fun users(room: String): List<RoomUser> = usersResult
    override suspend fun availableRooms(): List<AvailableRoom> = availableRoomsResult
    override suspend fun join(room: String) { joined += room }
    override suspend fun leave(room: String) { left += room }
    override suspend fun send(room: String, text: String) { sent += room to text }
}
