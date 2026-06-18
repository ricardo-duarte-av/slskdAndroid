package com.slskdandroid.core.data

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.AvailableRoom
import com.slskdandroid.core.model.RoomMessage
import com.slskdandroid.core.model.RoomUser
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.model.NetworkAvailableRoom
import com.slskdandroid.core.network.model.NetworkRoomMessage
import com.slskdandroid.core.network.model.NetworkRoomUser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

internal class DefaultRoomsRepository @Inject constructor(
    private val api: SlskdApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RoomsRepository {

    override fun joinedRooms(): Flow<List<String>> = flow {
        // No rooms push hub, so poll. First fetch may fail loudly; later blips are swallowed.
        var emittedOnce = false
        while (currentCoroutineContext().isActive) {
            runCatching { api.getJoinedRooms() }
                .onSuccess { emittedOnce = true; emit(it) }
                .onFailure { if (!emittedOnce) throw it }
            delay(JOINED_POLL_INTERVAL_MS)
        }
    }.flowOn(ioDispatcher)

    override fun messages(room: String): Flow<List<RoomMessage>> = flow {
        while (currentCoroutineContext().isActive) {
            // A failure (e.g. just-left room) yields an empty list rather than tearing the stream.
            val messages = runCatching { api.getRoomMessages(room) }.getOrDefault(emptyList())
            emit(messages.map(NetworkRoomMessage::toModel))
            delay(MESSAGES_POLL_INTERVAL_MS)
        }
    }.flowOn(ioDispatcher)

    override suspend fun users(room: String): List<RoomUser> = withContext(ioDispatcher) {
        api.getRoomUsers(room).map(NetworkRoomUser::toModel)
    }

    override suspend fun availableRooms(): List<AvailableRoom> = withContext(ioDispatcher) {
        api.getAvailableRooms().map(NetworkAvailableRoom::toModel)
    }

    override suspend fun join(room: String) {
        withContext(ioDispatcher) { api.joinRoom(room) }
    }

    override suspend fun leave(room: String) {
        withContext(ioDispatcher) { api.leaveRoom(room) }
    }

    override suspend fun send(room: String, text: String) {
        withContext(ioDispatcher) { api.sendRoomMessage(room, text) }
    }
}

private fun NetworkAvailableRoom.toModel() = AvailableRoom(
    name = name,
    userCount = userCount,
    isPrivate = isPrivate,
    isOwned = isOwned,
    isModerated = isModerated,
)

private fun NetworkRoomMessage.toModel() = RoomMessage(
    username = username,
    message = message,
    isSelf = self == true,
    timestampMillis = timestamp?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
)

private fun NetworkRoomUser.toModel() = RoomUser(
    username = username,
    countryCode = countryCode?.takeIf { it.isNotBlank() },
    status = status,
    fileCount = fileCount,
    averageSpeed = averageSpeed,
)

private const val JOINED_POLL_INTERVAL_MS = 3_000L
private const val MESSAGES_POLL_INTERVAL_MS = 2_000L
