package com.slskdandroid.core.data

import app.cash.turbine.test
import com.slskdandroid.core.network.model.NetworkAvailableRoom
import com.slskdandroid.core.network.model.NetworkRoomMessage
import com.slskdandroid.core.network.model.NetworkRoomUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRoomsRepositoryTest {

    @Test
    fun `joinedRooms emits the polled list`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getJoinedRooms() = listOf("lobby", "music")
        }
        val repository = DefaultRoomsRepository(api, dispatcher)

        repository.joinedRooms().test {
            assertEquals(listOf("lobby", "music"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `messages map sender, self flag, and tolerate an unparseable timestamp`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getRoomMessages(room: String) = listOf(
                NetworkRoomMessage(username = "alice", message = "hi", self = false, timestamp = "nonsense"),
                NetworkRoomMessage(username = "me", message = "yo", self = true, timestamp = null),
            )
        }
        val repository = DefaultRoomsRepository(api, dispatcher)

        repository.messages("lobby").test {
            val messages = awaitItem()
            assertEquals("alice", messages[0].username)
            assertEquals("hi", messages[0].message)
            assertFalse(messages[0].isSelf)
            assertNull(messages[0].timestampMillis)
            assertTrue(messages[1].isSelf)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `users map fields and blank country codes become null`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getRoomUsers(room: String) = listOf(
                NetworkRoomUser(username = "alice", countryCode = "FR", status = "Online", fileCount = 5, averageSpeed = 99),
                NetworkRoomUser(username = "bob", countryCode = "", status = null, fileCount = 0, averageSpeed = 0),
            )
        }
        val repository = DefaultRoomsRepository(api, dispatcher)

        val users = repository.users("lobby")

        assertEquals("FR", users[0].countryCode)
        assertEquals(5, users[0].fileCount)
        assertNull(users[1].countryCode)
    }

    @Test
    fun `availableRooms map fields`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getAvailableRooms() = listOf(
                NetworkAvailableRoom(name = "music", userCount = 42, isPrivate = true, isOwned = false, isModerated = true),
            )
        }
        val repository = DefaultRoomsRepository(api, dispatcher)

        val room = repository.availableRooms().single()

        assertEquals("music", room.name)
        assertEquals(42, room.userCount)
        assertTrue(room.isPrivate)
        assertTrue(room.isModerated)
    }

    @Test
    fun `join, leave, and send forward to the API`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val joined = mutableListOf<String>()
        val left = mutableListOf<String>()
        val sent = mutableListOf<Pair<String, String>>()
        val api = object : FakeSlskdApi() {
            override suspend fun joinRoom(roomName: String) { joined += roomName }
            override suspend fun leaveRoom(room: String) { left += room }
            override suspend fun sendRoomMessage(room: String, message: String) { sent += room to message }
        }
        val repository = DefaultRoomsRepository(api, dispatcher)

        repository.join("music")
        repository.leave("lobby")
        repository.send("music", "hello")

        assertEquals("music", joined.single())
        assertEquals("lobby", left.single())
        assertEquals("music" to "hello", sent.single())
    }
}
