package com.slskdandroid.feature.browse.impl

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.slskdandroid.core.data.BrowseProgress
import com.slskdandroid.core.model.BrowseDirectory
import com.slskdandroid.core.model.SearchResultFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val browseRepository = FakeBrowseRepository()
    private val downloadsRepository = FakeDownloadsRepository()

    private fun viewModel() =
        BrowseViewModel(SavedStateHandle(), browseRepository, downloadsRepository)

    @Test
    fun `submitting a username browses and shows the directory tree`() = runTest {
        browseRepository.browseFlow = {
            flowOf(BrowseProgress.Loaded(listOf(BrowseDirectory("Music\\Album", listOf(file("Music\\Album\\a.mp3"))))))
        }
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onAction(BrowseAction.QueryChanged("alice"))
            viewModel.onAction(BrowseAction.Submit)
            val tree = awaitItemWhere { it.phase is BrowsePhase.Tree }.phase as BrowsePhase.Tree
            assertEquals("alice", tree.username)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `downloading a file enqueues it for the browsed user`() = runTest {
        browseRepository.browseFlow = {
            flowOf(BrowseProgress.Loaded(listOf(BrowseDirectory("Music", listOf(file("Music\\a.mp3", 500))))))
        }
        val viewModel = viewModel()

        viewModel.onAction(BrowseAction.QueryChanged("alice"))
        viewModel.onAction(BrowseAction.Submit)
        viewModel.onAction(BrowseAction.Download(file("Music\\a.mp3", 500)))

        assertEquals(Triple("alice", "Music\\a.mp3", 500L), downloadsRepository.enqueued.single())
    }
}

private suspend fun ReceiveTurbine<BrowseUiState>.awaitItemWhere(
    predicate: (BrowseUiState) -> Boolean,
): BrowseUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

private fun file(filename: String, size: Long = 1) = SearchResultFile(
    filename = filename,
    sizeBytes = size,
    bitRate = null,
    lengthSeconds = null,
    bitDepth = null,
    sampleRate = null,
    isVariableBitRate = null,
    extension = null,
    isLocked = false,
)
