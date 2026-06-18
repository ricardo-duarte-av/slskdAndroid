package com.slskdandroid.core.data

import com.slskdandroid.core.model.Conversation
import com.slskdandroid.core.model.PrivateMessage
import kotlinx.coroutines.flow.Flow

/**
 * Exposes slskd's private-message conversations. slskd has no messaging SignalR hub (unlike
 * searches), so both [conversations] and [messages] poll the REST endpoints and emit the latest
 * snapshot on an interval until the collection is cancelled.
 */
interface ChatRepository {

    /** A live, polling stream of all conversations (including inactive ones). */
    fun conversations(): Flow<List<Conversation>>

    /**
     * A live, polling stream of the messages in the conversation with [username]. Emits an empty
     * list (rather than failing) while no conversation exists yet, so a brand-new thread renders.
     */
    fun messages(username: String): Flow<List<PrivateMessage>>

    /**
     * The existing conversation with [username], or null if none exists yet. A one-shot snapshot
     * used to decide whether a "Chat" action should open an existing thread or start a new one.
     */
    suspend fun findConversation(username: String): Conversation?

    /** Sends [text] as a private message to [username]. */
    suspend fun send(username: String, text: String)

    /** Marks all messages from [username] as read. */
    suspend fun acknowledge(username: String)
}
