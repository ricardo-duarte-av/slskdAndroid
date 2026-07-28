package com.slskdandroid.feature.downloads.impl

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.slskdandroid.core.model.Download
import com.slskdandroid.core.model.DownloadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import com.slskdandroid.core.designsystem.component.UiText
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeDownloadsRepository()

    @Test
    fun `groups downloads by user`() = runTest {
        repository.downloadsFlow = flowOf(listOf(download("1", "alice"), download("2", "alice"), download("3", "bob")))
        val viewModel = DownloadsViewModel(repository, UnconfinedTestDispatcher(testScheduler))

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
        val viewModel = DownloadsViewModel(repository, UnconfinedTestDispatcher(testScheduler))

        viewModel.uiState.test {
            val errored = awaitItemWhere { it.loadState is LoadState.Error }
            assertEquals(UiText.Raw("offline"), (errored.loadState as LoadState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling the selection cancels each selected transfer`() = runTest {
        repository.downloadsFlow = flowOf(listOf(download("1", "alice"), download("2", "bob")))
        val viewModel = DownloadsViewModel(repository, UnconfinedTestDispatcher(testScheduler))

        viewModel.uiState.test {
            awaitItemWhere { it.loadState is LoadState.Loaded }
            viewModel.onAction(DownloadsAction.StartSelection("1"))
            viewModel.onAction(DownloadsAction.CancelSelected)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(Triple("alice", "1", false), repository.cancelled.single())
    }

    @Test
    fun `a bulk remove reports how many went, and undo re-queues them`() = runTest {
        val a = download("1", "alice", DownloadState.Completed)
        val b = download("2", "bob", DownloadState.Completed)
        repository.downloadsFlow = flowOf(listOf(a, b))
        val viewModel = DownloadsViewModel(repository, UnconfinedTestDispatcher(testScheduler))

        viewModel.uiState.test {
            awaitItemWhere { it.loadState is LoadState.Loaded }
            viewModel.onAction(DownloadsAction.BulkRemove(RemoveFilter.Completed))
            cancelAndIgnoreRemainingEvents()
        }

        val event = viewModel.events.first()
        assertTrue(event is DownloadsEvent.Removed)
        assertEquals(2, (event as DownloadsEvent.Removed).count)

        // Undo re-enqueues by (username, filename, size) — the same call Retry makes.
        viewModel.onAction(DownloadsAction.UndoRemove(event.restorable))
        assertEquals(
            listOf(a.username to a.filename, b.username to b.filename),
            repository.enqueued.map { it.first to it.second },
        )
    }

    @Test
    fun `a failing bulk action is reported instead of silently swallowed`() = runTest {
        repository.downloadsFlow = flowOf(listOf(download("1", "alice", DownloadState.Completed)))
        repository.failCancel = true
        val viewModel = DownloadsViewModel(repository, UnconfinedTestDispatcher(testScheduler))

        viewModel.uiState.test {
            awaitItemWhere { it.loadState is LoadState.Loaded }
            viewModel.onAction(DownloadsAction.BulkRemove(RemoveFilter.Completed))
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(DownloadsEvent.Failed(failed = 1, attempted = 1), viewModel.events.first())
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

private fun download(
    id: String,
    username: String,
    state: DownloadState = DownloadState.InProgress,
) = Download(
    id = id,
    username = username,
    directory = "Music",
    filename = "Music\\$id.mp3",
    sizeBytes = 1,
    bytesTransferred = 0,
    averageSpeed = 0.0,
    percentComplete = 0.0,
    placeInQueue = null,
    state = state,
    rawState = state.name,
    exception = null,
)
