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
        // A 404 simply means no conversation exists yet (e.g. opened from a peer we've never
        // messaged) — the first failure yields an empty thread so the composer still renders.
        // Afterwards the last good snapshot is re-emitted instead: a transient network blip must
        // not blank out an open conversation for a poll cycle and then repopulate it.
        var lastGood: List<PrivateMessage>? = null
        while (currentCoroutineContext().isActive) {
            val messages = runCatching { api.getMessages(username) }
                .map { it.map(NetworkPrivateMessage::toModel) }
                .getOrElse { lastGood ?: emptyList() }
            lastGood = messages
            emit(messages)
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

    /**
     * Best-effort, but retried: a silently-dropped acknowledgement leaves the thread unread
     * server-side (and re-notifying) with nothing in the UI to indicate it, and the user has no way
     * to trigger another attempt short of reopening the thread.
     */
    override suspend fun acknowledge(username: String) {
        withContext(ioDispatcher) {
            repeat(ACKNOWLEDGE_ATTEMPTS) { attempt ->
                if (runCatching { api.acknowledgeConversation(username) }.isSuccess) return@withContext
                if (attempt < ACKNOWLEDGE_ATTEMPTS - 1) delay(ACKNOWLEDGE_RETRY_DELAY_MS)
            }
        }
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
private const val ACKNOWLEDGE_ATTEMPTS = 3
private const val ACKNOWLEDGE_RETRY_DELAY_MS = 1_000L
