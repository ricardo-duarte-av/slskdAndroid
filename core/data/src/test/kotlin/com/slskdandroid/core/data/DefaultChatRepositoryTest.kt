package com.slskdandroid.core.data

import app.cash.turbine.test
import com.slskdandroid.core.network.model.NetworkConversation
import com.slskdandroid.core.network.model.NetworkPrivateMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultChatRepositoryTest {

    @Test
    fun `conversations map unread bookkeeping`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getConversations(includeInactive: Boolean, unAcknowledgedOnly: Boolean) = listOf(
                NetworkConversation(username = "alice", isActive = true, unAcknowledgedMessageCount = 3, hasUnAcknowledgedMessages = true),
            )
        }
        val repository = DefaultChatRepository(api, dispatcher)

        repository.conversations().test {
            val conversation = awaitItem().single()
            assertEquals("alice", conversation.username)
            assertEquals(3, conversation.unreadCount)
            assertTrue(conversation.hasUnread)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `messages map direction to outgoing-incoming`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getMessages(username: String) = listOf(
                NetworkPrivateMessage(id = 1, username = "me", direction = "Out", message = "hi"),
                NetworkPrivateMessage(id = 2, username = "alice", direction = "In", message = "hey"),
            )
        }
        val repository = DefaultChatRepository(api, dispatcher)

        repository.messages("alice").test {
            val messages = awaitItem()
            assertTrue(messages[0].isOutgoing)
            assertFalse(messages[1].isOutgoing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `findConversation returns the matching thread or null`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getConversations(includeInactive: Boolean, unAcknowledgedOnly: Boolean) = listOf(
                NetworkConversation(username = "alice"),
                NetworkConversation(username = "bob"),
            )
        }
        val repository = DefaultChatRepository(api, dispatcher)

        assertEquals("bob", repository.findConversation("bob")?.username)
        assertNull(repository.findConversation("nobody"))
    }

    @Test
    fun `send and acknowledge forward to the API`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val sent = mutableListOf<Pair<String, String>>()
        val acknowledged = mutableListOf<String>()
        val api = object : FakeSlskdApi() {
            override suspend fun sendMessage(username: String, message: String) { sent += username to message }
            override suspend fun acknowledgeConversation(username: String) { acknowledged += username }
        }
        val repository = DefaultChatRepository(api, dispatcher)

        repository.send("alice", "hello")
        repository.acknowledge("alice")

        assertEquals("alice" to "hello", sent.single())
        assertEquals("alice", acknowledged.single())
    }
}
