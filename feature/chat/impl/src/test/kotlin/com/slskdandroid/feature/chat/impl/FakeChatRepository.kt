package com.slskdandroid.feature.chat.impl

import com.slskdandroid.core.data.AvatarRepository
import com.slskdandroid.core.data.ChatRepository
import com.slskdandroid.core.model.Conversation
import com.slskdandroid.core.model.PrivateMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Test double for [ChatRepository]; streams are configurable and actions are recorded. */
class FakeChatRepository : ChatRepository {

    var conversationsFlow: Flow<List<Conversation>> = flowOf(emptyList())
    var messagesFlow: (String) -> Flow<List<PrivateMessage>> = { flowOf(emptyList()) }
    var existingConversation: Conversation? = null

    val sent = mutableListOf<Pair<String, String>>()
    val acknowledged = mutableListOf<String>()

    override fun conversations(): Flow<List<Conversation>> = conversationsFlow
    override fun messages(username: String): Flow<List<PrivateMessage>> = messagesFlow(username)
    override suspend fun findConversation(username: String): Conversation? = existingConversation
    override suspend fun send(username: String, text: String) { sent += username to text }
    override suspend fun acknowledge(username: String) { acknowledged += username }
}

/** Avatars are irrelevant to these tests; always returns none. */
class FakeAvatarRepository : AvatarRepository {
    override suspend fun getAvatar(username: String): ByteArray? = null
}
