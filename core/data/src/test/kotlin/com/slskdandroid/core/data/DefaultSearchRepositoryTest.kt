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

    /**
     * Regression test: a search started from the app navigates to its detail screen the instant the
     * POST returns, which lands inside slskd's window where the record already carries the Completed
     * flag but `/responses` is still empty (slskd persists from its state-changed callback, then
     * attaches responses during finalization).
     *
     * The old code emitted that empty snapshot as final and closed the flow, so the screen showed
     * "no results" until it was closed and reopened. It must instead keep polling — and keep
     * reporting the search as running, so the UI holds its progress indicator.
     */
    @Test
    fun `search flagged complete before its responses land keeps polling instead of finishing empty`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        var poll = 0
        val api = object : FakeSlskdApi() {
            // Complete from the very first poll, and claiming two responses throughout.
            override suspend fun getSearch(id: String) =
                networkSearch(id, isComplete = true, responseCount = 2)

            // ...but /responses only serves them from the second poll onwards.
            override suspend fun getSearchResponses(id: String) = when (poll++) {
                0 -> emptyList()
                else -> listOf(networkResponse("alice"), networkResponse("bob"))
            }
        }
        val repository = DefaultSearchRepository(api, dispatcher)

        repository.observeSearch("s1").test {
            val first = awaitItem()
            assertTrue("must not report completion while responses are missing", !first.isComplete)
            assertEquals(emptyList<String>(), first.responses.map { it.username })

            val second = awaitItem()
            assertTrue(second.isComplete)
            assertEquals(listOf("alice", "bob"), second.responses.map { it.username }.sorted())

            awaitComplete()
        }
    }

    /**
     * The settling wait above is bounded: slskd logs a "record may be left 'hanging'" case when
     * finalization throws, which would leave the reported count permanently ahead of the responses
     * on offer. Rather than poll forever, give up and settle on what we have.
     */
    @Test
    fun `a completed search whose responses never arrive gives up after the settling window`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = object : FakeSlskdApi() {
            override suspend fun getSearch(id: String) =
                networkSearch(id, isComplete = true, responseCount = 5)

            override suspend fun getSearchResponses(id: String) = emptyList<NetworkSearchResponse>()
        }
        val repository = DefaultSearchRepository(api, dispatcher)

        repository.observeSearch("s1").test {
            // 15 settling ticks: the first 14 report the search as still running, the last gives up.
            repeat(14) { assertTrue(!awaitItem().isComplete) }
            val last = awaitItem()
            assertTrue(last.isComplete)
            assertEquals(emptyList<String>(), last.responses.map { it.username })
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

private fun networkSearch(id: String, isComplete: Boolean, responseCount: Int = 0) =
    NetworkSearch(id = id, searchText = "query", isComplete = isComplete, responseCount = responseCount)

private fun networkResponse(username: String) =
    NetworkSearchResponse(username = username, files = listOf(NetworkFile(filename = "$username.mp3")))
