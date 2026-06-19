package com.slskdandroid.feature.chat.impl

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.slskdandroid.core.model.Conversation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chatRepository = FakeChatRepository()
    private val avatarRepository = FakeAvatarRepository()

    private fun viewModel() =
        ChatViewModel(SavedStateHandle(), chatRepository, avatarRepository)

    @Test
    fun `loads the conversation list`() = runTest {
        chatRepository.conversationsFlow = flowOf(listOf(Conversation("alice", isActive = true, unreadCount = 0, hasUnread = false)))
        val viewModel = viewModel()

        viewModel.uiState.test {
            val list = awaitItemWhere { it.list is ListState.Loaded }.list as ListState.Loaded
            assertEquals(listOf("alice"), list.conversations.map { it.username })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening a thread and sending a draft forwards to the repository`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItemWhere { it.list is ListState.Loaded }
            viewModel.onAction(ChatAction.OpenConversation("alice"))
            awaitItemWhere { it.thread?.username == "alice" }
            viewModel.onAction(ChatAction.DraftChanged("hello"))
            viewModel.onAction(ChatAction.SendDraft)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("alice" to "hello", chatRepository.sent.single())
    }

    @Test
    fun `the composer sends to a new recipient and opens their thread`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItemWhere { it.list is ListState.Loaded }
            viewModel.onAction(ChatAction.StartNewChat)
            viewModel.onAction(ChatAction.ComposerUsernameChanged("bob"))
            viewModel.onAction(ChatAction.ComposerMessageChanged("yo"))
            viewModel.onAction(ChatAction.ComposerSubmit)
            awaitItemWhere { it.thread?.username == "bob" }
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("bob" to "yo", chatRepository.sent.single())
    }
}

private suspend fun ReceiveTurbine<ChatUiState>.awaitItemWhere(
    predicate: (ChatUiState) -> Boolean,
): ChatUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
