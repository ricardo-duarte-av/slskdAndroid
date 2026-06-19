package com.slskdandroid.core.data

import app.cash.turbine.test
import com.slskdandroid.core.network.SearchHubEvent
import com.slskdandroid.core.network.model.DirectoryContentsRequest
import com.slskdandroid.core.network.model.NetworkDirectory
import com.slskdandroid.core.network.model.NetworkFile
import com.slskdandroid.core.network.model.NetworkSearch
import com.slskdandroid.core.network.model.NetworkSearchResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DefaultSearchRepository.observeSearch] — the most intricate logic in the data
 * layer: it streams peer responses live off the SignalR hub, keys them by username, and reconciles
 * with a final REST fetch when slskd reports the search complete.
 *
 * Notes for anyone new to these:
 * - `runTest { }` runs the coroutine test with a virtual clock (no real delays/waiting).
 * - `UnconfinedTestDispatcher` runs launched coroutines eagerly, which makes the hub collector
 *   subscribe before we emit, so the assertions are deterministic (not racy).
 * - Turbine's `flow.test { }` lets us assert the exact sequence of emitted values with `awaitItem()`
 *   and that the flow finishes with `awaitComplete()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSearchRepositoryTest {

    @Test
    fun `live search emits each response then reconciles with REST on completion`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val hub = FakeSearchHub()
        val api = object : FakeSlskdApi() {
            // Not yet complete -> repository takes the live-hub path.
            override suspend fun getSearch(id: String) = networkSearch(id, isComplete = false)
            // The final REST fetch backfills "carol", who responded before the hub was attached.
            override suspend fun getSearchResponses(id: String) =
                listOf(networkResponse("alice"), networkResponse("carol"))
        }
        val repository = DefaultSearchRepository(api, hub, dispatcher)

        repository.observeSearch("s1").test {
            // Starts by emitting an empty, in-progress snapshot.
            assertEquals(emptyList<String>(), awaitItem().responses.map { it.username })

            hub.emit(SearchHubEvent.Response("s1", networkResponse("alice")))
            assertEquals(listOf("alice"), awaitItem().responses.map { it.username }.sorted())

            hub.emit(SearchHubEvent.Response("s1", networkResponse("bob")))
            assertEquals(listOf("alice", "bob"), awaitItem().responses.map { it.username }.sorted())

            // Completion: reconciles the live set (alice, bob) with REST (alice, carol).
            hub.emit(SearchHubEvent.Update("s1", isComplete = true))
            val final = awaitItem()
            assertTrue(final.isComplete)
            assertEquals(listOf("alice", "bob", "carol"), final.responses.map { it.username }.sorted())

            awaitComplete()
        }
    }

    @Test
    fun `responses for another search id are ignored`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val hub = FakeSearchHub()
        val api = object : FakeSlskdApi() {
            override suspend fun getSearch(id: String) = networkSearch(id, isComplete = false)
        }
        val repository = DefaultSearchRepository(api, hub, dispatcher)

        repository.observeSearch("s1").test {
            assertEquals(emptyList<String>(), awaitItem().responses.map { it.username })

            // A response for a different search must not produce an emission.
            hub.emit(SearchHubEvent.Response("OTHER", networkResponse("intruder")))
            // The one for our search does.
            hub.emit(SearchHubEvent.Response("s1", networkResponse("alice")))
            assertEquals(listOf("alice"), awaitItem().responses.map { it.username })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `already-complete search resolves from REST and completes without the hub`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getSearch(id: String) = networkSearch(id, isComplete = true)
            override suspend fun getSearchResponses(id: String) = listOf(networkResponse("alice"))
        }
        val repository = DefaultSearchRepository(api, FakeSearchHub(), dispatcher)

        repository.observeSearch("s1").test {
            val item = awaitItem()
            assertTrue(item.isComplete)
            assertEquals(listOf("alice"), item.responses.map { it.username })
            awaitComplete()
        }
    }

    @Test
    fun `getDirectoryFiles prefixes peer filenames with the directory path`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getDirectoryContents(
                username: String,
                request: DirectoryContentsRequest,
            ) = listOf(
                NetworkDirectory(
                    name = request.directory,
                    files = listOf(NetworkFile(filename = "song.mp3")),
                ),
            )
        }
        val repository = DefaultSearchRepository(api, FakeSearchHub(), dispatcher)

        val files = repository.getDirectoryFiles("alice", "Music\\Album")

        assertEquals(listOf("Music\\Album\\song.mp3"), files.map { it.filename })
    }
}

private fun networkSearch(id: String, isComplete: Boolean) =
    NetworkSearch(id = id, searchText = "query", isComplete = isComplete)

private fun networkResponse(username: String) =
    NetworkSearchResponse(username = username, files = listOf(NetworkFile(filename = "$username.mp3")))
