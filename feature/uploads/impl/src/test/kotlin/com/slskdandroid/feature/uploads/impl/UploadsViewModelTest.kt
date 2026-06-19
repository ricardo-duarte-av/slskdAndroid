package com.slskdandroid.feature.uploads.impl

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.slskdandroid.core.model.Upload
import com.slskdandroid.core.model.UploadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeUploadsRepository()

    @Test
    fun `groups uploads by user`() = runTest {
        repository.uploadsFlow = flowOf(listOf(upload("1", "alice"), upload("2", "bob")))
        val viewModel = UploadsViewModel(repository)

        viewModel.uiState.test {
            val loaded = awaitItemWhere { it.loadState is LoadState.Loaded }
            assertEquals(listOf("alice", "bob"), loaded.users.map { it.username })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing stream maps to an error state`() = runTest {
        repository.uploadsFlow = flow { throw RuntimeException("offline") }
        val viewModel = UploadsViewModel(repository)

        viewModel.uiState.test {
            val errored = awaitItemWhere { it.loadState is LoadState.Error }
            assertEquals("offline", (errored.loadState as LoadState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing the selection cancels-and-removes each selected transfer`() = runTest {
        repository.uploadsFlow = flowOf(listOf(upload("1", "alice"), upload("2", "bob")))
        val viewModel = UploadsViewModel(repository)

        viewModel.uiState.test {
            awaitItemWhere { it.loadState is LoadState.Loaded }
            viewModel.onAction(UploadsAction.StartSelection("2"))
            viewModel.onAction(UploadsAction.RemoveSelected)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(Triple("bob", "2", true), repository.cancelled.single())
    }
}

private suspend fun ReceiveTurbine<UploadsUiState>.awaitItemWhere(
    predicate: (UploadsUiState) -> Boolean,
): UploadsUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

private fun upload(id: String, username: String) = Upload(
    id = id,
    username = username,
    directory = "Shared",
    filename = "Shared\\$id.mp3",
    sizeBytes = 1,
    bytesTransferred = 0,
    averageSpeed = 0.0,
    percentComplete = 0.0,
    placeInQueue = null,
    state = UploadState.InProgress,
    rawState = "InProgress",
    exception = null,
)
