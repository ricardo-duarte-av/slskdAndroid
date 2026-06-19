package com.slskdandroid.core.data

import app.cash.turbine.test
import com.slskdandroid.core.model.Download
import com.slskdandroid.core.model.DownloadState
import com.slskdandroid.core.network.model.NetworkDownloadDirectory
import com.slskdandroid.core.network.model.NetworkTransfer
import com.slskdandroid.core.network.model.NetworkUserDownloads
import com.slskdandroid.core.network.model.QueueDownloadRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDownloadsRepositoryTest {

    @Test
    fun `downloads flattens users-then-directories-then-files and maps state`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getDownloads() = listOf(
                NetworkUserDownloads(
                    username = "alice",
                    directories = listOf(
                        NetworkDownloadDirectory(
                            directory = "Music",
                            files = listOf(
                                transfer("1", "Music\\a.mp3", "InProgress"),
                                transfer("2", "Music\\b.mp3", "Completed, Succeeded"),
                            ),
                        ),
                    ),
                ),
            )
        }
        val repository = DefaultDownloadsRepository(api, dispatcher)

        repository.downloads().test {
            val list = awaitItem()
            assertEquals(listOf("1", "2"), list.map { it.id })
            assertEquals("Music", list[0].directory)
            assertEquals(DownloadState.InProgress, list[0].state)
            assertEquals(DownloadState.Completed, list[1].state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `raw transfer state strings collapse to coarse download states`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getDownloads() = listOf(
                NetworkUserDownloads(
                    "u",
                    listOf(
                        NetworkDownloadDirectory(
                            "d",
                            listOf(
                                transfer("q", "d\\q", "Queued, Remotely"),
                                transfer("e", "d\\e", "Completed, Errored"),
                                transfer("c", "d\\c", "Completed, Cancelled"),
                                transfer("x", "d\\x", "Something"),
                            ),
                        ),
                    ),
                ),
            )
        }
        val repository = DefaultDownloadsRepository(api, dispatcher)

        repository.downloads().test {
            val byId = awaitItem().associateBy { it.id }
            assertEquals(DownloadState.Queued, byId.getValue("q").state)
            assertEquals(DownloadState.Failed, byId.getValue("e").state)
            assertEquals(DownloadState.Failed, byId.getValue("c").state)
            assertEquals(DownloadState.Unknown, byId.getValue("x").state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancel, enqueue, and retry forward to the API`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val cancelled = mutableListOf<Triple<String, String, Boolean>>()
        val enqueued = mutableListOf<Pair<String, QueueDownloadRequest>>()
        val api = object : FakeSlskdApi() {
            override suspend fun cancelDownload(username: String, id: String, remove: Boolean) {
                cancelled += Triple(username, id, remove)
            }

            override suspend fun enqueueDownloads(username: String, files: List<QueueDownloadRequest>) {
                files.forEach { enqueued += username to it }
            }
        }
        val repository = DefaultDownloadsRepository(api, dispatcher)

        repository.cancel("alice", "1", remove = true)
        repository.enqueue("bob", "f.mp3", 123L)
        repository.retry(download("9", "bob", "g.mp3", 456L))

        assertEquals(Triple("alice", "1", true), cancelled.single())
        assertEquals("bob" to QueueDownloadRequest("f.mp3", 123L), enqueued[0])
        // retry re-enqueues by the download's filename + size.
        assertEquals("bob" to QueueDownloadRequest("g.mp3", 456L), enqueued[1])
    }
}

private fun transfer(id: String, filename: String, state: String) =
    NetworkTransfer(id = id, username = "alice", filename = filename, size = 100, state = state)

private fun download(id: String, username: String, filename: String, size: Long) = Download(
    id = id,
    username = username,
    directory = "d",
    filename = filename,
    sizeBytes = size,
    bytesTransferred = 0,
    averageSpeed = 0.0,
    percentComplete = 0.0,
    placeInQueue = null,
    state = DownloadState.Failed,
    rawState = "Completed, Errored",
    exception = null,
)
