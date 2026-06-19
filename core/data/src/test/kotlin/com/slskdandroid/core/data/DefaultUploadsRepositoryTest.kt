package com.slskdandroid.core.data

import app.cash.turbine.test
import com.slskdandroid.core.model.UploadState
import com.slskdandroid.core.network.model.NetworkDownloadDirectory
import com.slskdandroid.core.network.model.NetworkTransfer
import com.slskdandroid.core.network.model.NetworkUserDownloads
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUploadsRepositoryTest {

    @Test
    fun `uploads flattens the grouping and maps state`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getUploads() = listOf(
                NetworkUserDownloads(
                    username = "bob",
                    directories = listOf(
                        NetworkDownloadDirectory(
                            directory = "Shared",
                            files = listOf(
                                NetworkTransfer(id = "1", username = "bob", filename = "Shared\\a.mp3", state = "InProgress"),
                                NetworkTransfer(id = "2", username = "bob", filename = "Shared\\b.mp3", state = "Completed, Succeeded"),
                            ),
                        ),
                    ),
                ),
            )
        }
        val repository = DefaultUploadsRepository(api, dispatcher)

        repository.uploads().test {
            val list = awaitItem()
            assertEquals(listOf("1", "2"), list.map { it.id })
            assertEquals("Shared", list[0].directory)
            assertEquals(UploadState.InProgress, list[0].state)
            assertEquals(UploadState.Completed, list[1].state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancel forwards to the API`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val cancelled = mutableListOf<Triple<String, String, Boolean>>()
        val api = object : FakeSlskdApi() {
            override suspend fun cancelUpload(username: String, id: String, remove: Boolean) {
                cancelled += Triple(username, id, remove)
            }
        }
        val repository = DefaultUploadsRepository(api, dispatcher)

        repository.cancel("bob", "7", remove = false)

        assertEquals(Triple("bob", "7", false), cancelled.single())
    }
}
