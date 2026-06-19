package com.slskdandroid.core.data

import app.cash.turbine.test
import com.slskdandroid.core.network.model.NetworkBrowseResponse
import com.slskdandroid.core.network.model.NetworkDirectory
import com.slskdandroid.core.network.model.NetworkFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBrowseRepositoryTest {

    @Test
    fun `browse emits Loading then Loaded with non-empty directories and prefixed filenames`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getBrowse(username: String) = NetworkBrowseResponse(
                directories = listOf(
                    NetworkDirectory(name = "Music\\Album", files = listOf(NetworkFile(filename = "a.mp3"))),
                    NetworkDirectory(name = "Empty", files = emptyList()), // dropped: no files
                ),
                lockedDirectories = listOf(
                    NetworkDirectory(name = "Private", files = listOf(NetworkFile(filename = "secret.flac"))),
                ),
            )

            // 404 while no browse is tracked; the repository tolerates it.
            override suspend fun getBrowseStatus(username: String): Double = throw IOException("no browse running")
        }
        val repository = DefaultBrowseRepository(api, dispatcher)

        repository.browse("alice").test {
            assertEquals(BrowseProgress.Loading(null), awaitItem())

            val loaded = awaitItem() as BrowseProgress.Loaded
            // Unlocked first, then locked; the empty directory is filtered out.
            assertEquals(listOf("Music\\Album", "Private"), loaded.directories.map { it.directory })
            // Backslash path keeps "\\"; the file is prefixed with its directory.
            assertEquals("Music\\Album\\a.mp3", loaded.directories[0].files.single().filename)
            assertFalse(loaded.directories[0].files.single().isLocked)
            // Forward-slash style path; locked files carry the locked flag.
            assertEquals("Private/secret.flac", loaded.directories[1].files.single().filename)
            assertTrue(loaded.directories[1].files.single().isLocked)

            awaitComplete()
        }
    }
}
