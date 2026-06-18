package com.slskdandroid.core.data

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.Conversation
import com.slskdandroid.core.model.PrivateMessage
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.model.NetworkConversation
import com.slskdandroid.core.network.model.NetworkPrivateMessage
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

internal class DefaultChatRepository @Inject constructor(
    private val api: SlskdApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ChatRepository {

    override fun conversations(): Flow<List<Conversation>> = flow {
        // No messaging push hub exists, so poll. The first fetch may fail loudly (so the UI can
        // surface a connection error); once we've emitted, transient blips are swallowed to keep
        // the list alive — mirrors the transfers repositories.
        var emittedOnce = false
        while (currentCoroutineContext().isActive) {
            runCatching { api.getConversations(includeInactive = true, unAcknowledgedOnly = false) }
                .onSuccess { emittedOnce = true; emit(it.map(NetworkConversation::toModel)) }
                .onFailure { if (!emittedOnce) throw it }
            delay(CONVERSATIONS_POLL_INTERVAL_MS)
        }
    }.flowOn(ioDispatcher)

    override fun messages(username: String): Flow<List<PrivateMessage>> = flow {
        while (currentCoroutineContext().isActive) {
            // A 404 simply means no conversation exists yet (e.g. opened from a peer we've never
            // messaged) — treat it as an empty thread so the composer still renders.
            val messages = runCatching { api.getMessages(username) }.getOrDefault(emptyList())
            emit(messages.map(NetworkPrivateMessage::toModel))
            delay(MESSAGES_POLL_INTERVAL_MS)
        }
    }.flowOn(ioDispatcher)

    override suspend fun findConversation(username: String): Conversation? = withContext(ioDispatcher) {
        runCatching {
            api.getConversations(includeInactive = true, unAcknowledgedOnly = false)
                .map(NetworkConversation::toModel)
                .firstOrNull { it.username == username }
        }.getOrNull()
    }

    override suspend fun send(username: String, text: String) {
        withContext(ioDispatcher) { api.sendMessage(username, text) }
    }

    override suspend fun acknowledge(username: String) {
        withContext(ioDispatcher) { runCatching { api.acknowledgeConversation(username) } }
    }
}

private fun NetworkConversation.toModel() = Conversation(
    username = username,
    isActive = isActive,
    unreadCount = unAcknowledgedMessageCount,
    hasUnread = hasUnAcknowledgedMessages,
)

private fun NetworkPrivateMessage.toModel() = PrivateMessage(
    id = id,
    username = username,
    message = message,
    isOutgoing = direction.equals("Out", ignoreCase = true),
    isAcknowledged = isAcknowledged,
    timestampMillis = timestamp?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
)

private const val CONVERSATIONS_POLL_INTERVAL_MS = 3_000L
private const val MESSAGES_POLL_INTERVAL_MS = 2_000L
