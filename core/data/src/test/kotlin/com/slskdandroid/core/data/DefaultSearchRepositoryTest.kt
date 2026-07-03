package com.slskdandroid.core.data

import app.cash.turbine.test
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
import java.io.IOException

/**
 * Unit tests for [DefaultSearchRepository.observeSearch] — the most intricate logic in the data
 * layer: it polls the search + its responses over REST, keyed-by-peer, until slskd reports the
 * search complete.
 *
 * Notes for anyone new to these:
 * - `runTest { }` runs the coroutine test with a virtual clock, so the poll `delay`s take no real
 *   time — Turbine advances the clock as it awaits each emission.
 * - `UnconfinedTestDispatcher` runs launched coroutines eagerly, keeping the assertions
 *   deterministic (not racy).
 * - Turbine's `flow.test { }` lets us assert the exact sequence of emitted values with `awaitItem()`
 *   and that the flow finishes with `awaitComplete()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSearchRepositoryTest {

    /**
     * Regression test for issue #2 ("Cannot open a search while it's ongoing"): opening an
     * in-progress search must stream a growing result set and finish cleanly — no SignalR handshake
     * involved. Each REST poll returns one more peer until slskd flips `isComplete`.
     */
    @Test
    fun `ongoing search streams growing responses via REST polling until complete`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        // slskd's view across successive polls: in-progress, in-progress, then complete.
        val completeByPoll = listOf(false, false, true)
        val responsesByPoll = listOf(
            listOf(networkResponse("alice")),
            listOf(networkResponse("alice"), networkResponse("bob")),
            listOf(networkResponse("alice"), networkResponse("bob"), networkResponse("carol")),
        )
        var poll = 0
        val api = object : FakeSlskdApi() {
            override suspend fun getSearch(id: String) =
                networkSearch(id, isComplete = completeByPoll[poll])

            // Called second within a poll; advances the poll cursor for the next round.
            override suspend fun getSearchResponses(id: String) = responsesByPoll[poll].also { poll++ }
        }
        val repository = DefaultSearchRepository(api, dispatcher)

        repository.observeSearch("s1").test {
            assertEquals(listOf("alice"), awaitItem().responses.map { it.username }.sorted())
            assertEquals(listOf("alice", "bob"), awaitItem().responses.map { it.username }.sorted())

            val final = awaitItem()
            assertTrue(final.isComplete)
            assertEquals(listOf("alice", "bob", "carol"), final.responses.map { it.username }.sorted())

            awaitComplete()
        }
    }

    @Test
    fun `already-complete search resolves from REST and completes in one emission`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getSearch(id: String) = networkSearch(id, isComplete = true)
            override suspend fun getSearchResponses(id: String) = listOf(networkResponse("alice"))
        }
        val repository = DefaultSearchRepository(api, dispatcher)

        repository.observeSearch("s1").test {
            val item = awaitItem()
            assertTrue(item.isComplete)
            assertEquals(listOf("alice"), item.responses.map { it.username })
            awaitComplete()
        }
    }

    @Test
    fun `a transient poll failure after the first emission is tolerated and polling continues`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        var calls = 0
        val api = object : FakeSlskdApi() {
            override suspend fun getSearch(id: String): NetworkSearch = when (calls++) {
                0 -> networkSearch(id, isComplete = false)
                1 -> throw IOException("transient blip")
                else -> networkSearch(id, isComplete = true)
            }

            override suspend fun getSearchResponses(id: String) = listOf(networkResponse("alice"))
        }
        val repository = DefaultSearchRepository(api, dispatcher)

        repository.observeSearch("s1").test {
            // First poll: in progress. Second poll throws and is swallowed (no emission). Third
            // poll recovers and completes.
            assertTrue(!awaitItem().isComplete)
            assertTrue(awaitItem().isComplete)
            awaitComplete()
        }
    }

    @Test
    fun `an error on the very first poll surfaces as a flow error`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getSearch(id: String): NetworkSearch = throw IOException("boom")
        }
        val repository = DefaultSearchRepository(api, dispatcher)

        repository.observeSearch("s1").test {
            assertEquals("boom", awaitError().message)
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
        val repository = DefaultSearchRepository(api, dispatcher)

        val files = repository.getDirectoryFiles("alice", "Music\\Album")

        assertEquals(listOf("Music\\Album\\song.mp3"), files.map { it.filename })
    }
}

private fun networkSearch(id: String, isComplete: Boolean) =
    NetworkSearch(id = id, searchText = "query", isComplete = isComplete)

private fun networkResponse(username: String) =
    NetworkSearchResponse(username = username, files = listOf(NetworkFile(filename = "$username.mp3")))
