package com.slskdandroid.feature.downloads.impl

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.slskdandroid.core.model.Download
import com.slskdandroid.core.model.DownloadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeDownloadsRepository()

    @Test
    fun `groups downloads by user`() = runTest {
        repository.downloadsFlow = flowOf(listOf(download("1", "alice"), download("2", "alice"), download("3", "bob")))
        val viewModel = DownloadsViewModel(repository)

        viewModel.uiState.test {
            val loaded = awaitItemWhere { it.loadState is LoadState.Loaded }
            assertEquals(listOf("alice", "bob"), loaded.users.map { it.username })
            assertEquals(2, loaded.users.first().fileCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing stream maps to an error state`() = runTest {
        repository.downloadsFlow = flow { throw RuntimeException("offline") }
        val viewModel = DownloadsViewModel(repository)

        viewModel.uiState.test {
            val errored = awaitItemWhere { it.loadState is LoadState.Error }
            assertEquals("offline", (errored.loadState as LoadState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling the selection cancels each selected transfer`() = runTest {
        repository.downloadsFlow = flowOf(listOf(download("1", "alice"), download("2", "bob")))
        val viewModel = DownloadsViewModel(repository)

        viewModel.uiState.test {
            awaitItemWhere { it.loadState is LoadState.Loaded }
            viewModel.onAction(DownloadsAction.StartSelection("1"))
            viewModel.onAction(DownloadsAction.CancelSelected)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(Triple("alice", "1", false), repository.cancelled.single())
    }
}

private suspend fun ReceiveTurbine<DownloadsUiState>.awaitItemWhere(
    predicate: (DownloadsUiState) -> Boolean,
): DownloadsUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

private fun download(id: String, username: String) = Download(
    id = id,
    username = username,
    directory = "Music",
    filename = "Music\\$id.mp3",
    sizeBytes = 1,
    bytesTransferred = 0,
    averageSpeed = 0.0,
    percentComplete = 0.0,
    placeInQueue = null,
    state = DownloadState.InProgress,
    rawState = "InProgress",
    exception = null,
)
