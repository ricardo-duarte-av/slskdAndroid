package com.slskdandroid.feature.rooms.impl

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRoomsRepository()

    @Test
    fun `loads the joined-room list`() = runTest {
        repository.joinedRoomsFlow = flowOf(listOf("lobby", "music"))
        val viewModel = RoomsViewModel(repository)

        viewModel.uiState.test {
            val list = awaitItemWhere { it.list is ListState.Loaded }.list as ListState.Loaded
            assertEquals(listOf("lobby", "music"), list.rooms)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening a room and sending a message forwards to the repository`() = runTest {
        repository.joinedRoomsFlow = flowOf(listOf("lobby"))
        val viewModel = RoomsViewModel(repository)

        viewModel.uiState.test {
            awaitItemWhere { it.list is ListState.Loaded }
            viewModel.onAction(RoomsAction.OpenRoom("lobby"))
            awaitItemWhere { it.open?.name == "lobby" }
            viewModel.onAction(RoomsAction.DraftChanged("hello"))
            viewModel.onAction(RoomsAction.SendMessage)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("lobby" to "hello", repository.sent.single())
    }
}

private suspend fun ReceiveTurbine<RoomsUiState>.awaitItemWhere(
    predicate: (RoomsUiState) -> Boolean,
): RoomsUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
