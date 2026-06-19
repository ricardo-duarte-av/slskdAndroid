package com.slskdandroid.core.data

import com.slskdandroid.core.model.UserPresence
import com.slskdandroid.core.network.model.NetworkUserEndpoint
import com.slskdandroid.core.network.model.NetworkUserInfo
import com.slskdandroid.core.network.model.NetworkUserStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUsersRepositoryTest {

    @Test
    fun `getUser aggregates info, status, and endpoint`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getUserInfo(username: String) = NetworkUserInfo(
                description = "hi there",
                hasFreeUploadSlot = true,
                uploadSlots = 2,
                queueLength = 5,
            )

            override suspend fun getUserStatus(username: String) =
                NetworkUserStatus(isPrivileged = true, presence = "Online")

            override suspend fun getUserEndpoint(username: String) =
                NetworkUserEndpoint(address = "1.2.3.4", port = 5030)
        }
        val repository = DefaultUsersRepository(api, dispatcher)

        val profile = repository.getUser("alice")

        assertEquals("alice", profile.username)
        assertEquals(UserPresence.Online, profile.presence)
        assertTrue(profile.isPrivileged)
        assertTrue(profile.hasFreeUploadSlot)
        assertEquals(2, profile.uploadSlots)
        assertEquals("1.2.3.4", profile.ipAddress)
        assertEquals(5030, profile.port)
        assertEquals("hi there", profile.description)
    }

    @Test
    fun `getUser tolerates failing status and endpoint lookups`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getUserInfo(username: String) = NetworkUserInfo(hasFreeUploadSlot = false)
            override suspend fun getUserStatus(username: String): NetworkUserStatus = throw IOException("offline")
            override suspend fun getUserEndpoint(username: String): NetworkUserEndpoint = throw IOException("offline")
        }
        val repository = DefaultUsersRepository(api, dispatcher)

        val profile = repository.getUser("ghost")

        assertEquals(UserPresence.Unknown, profile.presence)
        assertFalse(profile.isPrivileged)
        assertNull(profile.ipAddress)
        assertNull(profile.port)
    }
}
