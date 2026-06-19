package com.slskdandroid.feature.search.impl

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.slskdandroid.core.model.Search
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [SearchListViewModel]: that user actions drive the right repository calls and the
 * exposed `uiState`/`openSearch` reflect them. Uses a hand-written [FakeSearchRepository] — no
 * mocking framework — so the tests read as plain Kotlin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSearchRepository()

    @Test
    fun `loads searches from the repository into uiState`() = runTest {
        repository.searchesFlow = flow { emit(listOf(search("1"), search("2"))) }
        val viewModel = SearchListViewModel(repository)

        viewModel.uiState.test {
            val loaded = awaitItemWhere { it.searches is SearchesState.Loaded }.searches
            assertEquals(listOf("1", "2"), (loaded as SearchesState.Loaded).searches.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query changes are reflected in uiState`() = runTest {
        val viewModel = SearchListViewModel(repository)

        viewModel.uiState.test {
            viewModel.onAction(SearchListAction.QueryChanged("aphex"))
            assertEquals("aphex", awaitItemWhere { it.query == "aphex" }.query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submitting a blank query does not start a search`() = runTest {
        val viewModel = SearchListViewModel(repository)

        viewModel.onAction(SearchListAction.QueryChanged("   "))
        viewModel.onAction(SearchListAction.Submit)

        assertTrue(repository.startedQueries.isEmpty())
    }

    @Test
    fun `submitting a query starts the search and emits an open event`() = runTest {
        val viewModel = SearchListViewModel(repository)

        viewModel.openSearch.test {
            viewModel.onAction(SearchListAction.QueryChanged("aphex twin"))
            viewModel.onAction(SearchListAction.Submit)

            assertEquals("id-aphex twin", awaitItem())
        }
        assertEquals(listOf("aphex twin"), repository.startedQueries)
    }

    @Test
    fun `a successful submit clears the query field`() = runTest {
        val viewModel = SearchListViewModel(repository)

        viewModel.uiState.test {
            viewModel.onAction(SearchListAction.QueryChanged("aphex twin"))
            assertEquals("aphex twin", awaitItemWhere { it.query == "aphex twin" }.query)

            viewModel.onAction(SearchListAction.Submit)
            assertEquals("", awaitItemWhere { it.query == "" }.query)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete action forwards the id to the repository`() = runTest {
        val viewModel = SearchListViewModel(repository)

        viewModel.onAction(SearchListAction.Delete("search-7"))

        assertEquals(listOf("search-7"), repository.deletedIds)
    }

    @Test
    fun `a failing searches stream maps to an Error state`() = runTest {
        repository.searchesFlow = flow { throw RuntimeException("network down") }
        val viewModel = SearchListViewModel(repository)

        viewModel.uiState.test {
            val errored = awaitItemWhere { it.searches is SearchesState.Error }
            assertEquals("network down", (errored.searches as SearchesState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** Awaits items until one matches [predicate] (skips intermediate emissions we don't care about). */
private suspend fun ReceiveTurbine<SearchListUiState>.awaitItemWhere(
    predicate: (SearchListUiState) -> Boolean,
): SearchListUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

private fun search(id: String) = Search(
    id = id,
    searchText = "query $id",
    state = "Completed",
    isComplete = true,
    fileCount = 0,
    lockedFileCount = 0,
    responseCount = 0,
    startedAt = null,
    endedAt = null,
)
