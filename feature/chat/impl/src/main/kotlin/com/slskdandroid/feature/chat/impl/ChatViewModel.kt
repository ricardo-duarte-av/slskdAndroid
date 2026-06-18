package com.slskdandroid.feature.chat.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.AvatarRepository
import com.slskdandroid.core.data.ChatRepository
import com.slskdandroid.core.model.Conversation
import com.slskdandroid.core.model.PrivateMessage
import com.slskdandroid.feature.chat.api.CHAT_USER_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {

    // Mutable UI inputs. The polled data hangs off these via flatMapLatest, and the whole chain is
    // collected through stateIn(WhileSubscribed), so nothing polls while the tab is off-screen.
    private val retryTrigger = MutableStateFlow(0)
    private val openUsername = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow("")
    private val sending = MutableStateFlow(false)
    private val composer = MutableStateFlow<ComposerState?>(null)
    private val avatars = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    /** Usernames we've already kicked an avatar fetch for, so each is requested at most once. */
    private val requestedAvatars = HashSet<String>()

    private val listState: Flow<ListState> = retryTrigger.flatMapLatest {
        chatRepository.conversations()
            .onEach { conversations -> ensureAvatars(conversations.map { it.username }) }
            .map<List<Conversation>, ListState> { ListState.Loaded(it) }
            .onStart { emit(ListState.Loading) }
            .catch { e -> emit(ListState.Error(e.message ?: "Couldn't load conversations.")) }
    }

    private val threadState: Flow<ThreadState?> =
        combine(threadMessagesFlow(), draft, sending) { thread, draftText, isSending ->
            thread?.let {
                ThreadState(
                    username = it.username,
                    messages = it.messages.orEmpty(),
                    loading = it.messages == null,
                    draft = draftText,
                    sending = isSending,
                )
            }
        }

    val uiState: StateFlow<ChatUiState> =
        combine(listState, threadState, composer, avatars) { list, thread, comp, avatarMap ->
            ChatUiState(list = list, thread = thread, composer = comp, avatars = avatarMap)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatUiState(),
        )

    init {
        // Deep-linked from a peer (chat/{username}, the Search/Downloads/Uploads/Users "Chat"
        // action). If we already have a conversation with them, open that thread; otherwise open
        // the composer pre-filled, asking for the first message.
        savedStateHandle.get<String>(CHAT_USER_ARG)?.takeIf { it.isNotBlank() }?.let { username ->
            viewModelScope.launch {
                val existing = chatRepository.findConversation(username)
                if (existing != null) {
                    openUsername.value = existing.username
                } else {
                    composer.value = ComposerState(username = username, usernameLocked = true)
                }
                ensureAvatars(listOf(existing?.username ?: username))
            }
        }
    }

    /** Polls the open conversation's messages (null username → list view, no poll). */
    private fun threadMessagesFlow(): Flow<ThreadMessages?> =
        openUsername.flatMapLatest { username ->
            if (username == null) {
                flowOf<ThreadMessages?>(null)
            } else {
                chatRepository.messages(username)
                    .map { ThreadMessages(username, it) }
                    // null messages == still loading the first poll; also mark the thread read.
                    .onStart {
                        chatRepository.acknowledge(username)
                        emit(ThreadMessages(username, null))
                    }
            }
        }

    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.OpenConversation -> {
                draft.value = ""
                sending.value = false
                openUsername.value = action.username
                ensureAvatars(listOf(action.username))
            }
            ChatAction.CloseConversation -> openUsername.value = null
            is ChatAction.DraftChanged -> draft.value = action.text
            ChatAction.SendDraft -> sendDraft()
            ChatAction.RetryList -> retryTrigger.value++

            ChatAction.StartNewChat -> composer.value = ComposerState()
            is ChatAction.ComposerUsernameChanged ->
                composer.update { it.copy(username = action.username, error = null) }
            is ChatAction.ComposerMessageChanged ->
                composer.update { it.copy(message = action.message, error = null) }
            ChatAction.ComposerSubmit -> submitComposer()
            ChatAction.ComposerDismiss -> composer.value = null
        }
    }

    private fun sendDraft() {
        val username = openUsername.value ?: return
        val text = draft.value.trim()
        if (text.isEmpty() || sending.value) return
        sending.value = true
        viewModelScope.launch {
            runCatching { chatRepository.send(username, text) }
                // Clear the draft on success; the message poll will surface the sent message.
                .onSuccess { draft.value = "" }
            sending.value = false
        }
    }

    private fun submitComposer() {
        val current = composer.value ?: return
        val username = current.username.trim()
        val text = current.message.trim()
        if (current.sending) return
        when {
            username.isEmpty() -> composer.update { it.copy(error = "Enter a username.") }
            text.isEmpty() -> composer.update { it.copy(error = "Enter a message.") }
            else -> {
                composer.update { it.copy(sending = true, error = null) }
                viewModelScope.launch {
                    runCatching { chatRepository.send(username, text) }
                        .onSuccess {
                            composer.value = null
                            draft.value = ""
                            sending.value = false
                            openUsername.value = username
                            ensureAvatars(listOf(username))
                        }
                        .onFailure { e ->
                            composer.update {
                                it.copy(sending = false, error = e.message ?: "Couldn't send message.")
                            }
                        }
                }
            }
        }
    }

    /**
     * Kicks an avatar fetch for any [usernames] not yet requested this session; results trickle
     * into [avatars] as they resolve. Runs on the (single-threaded) main dispatcher, so the
     * [requestedAvatars] guard needs no extra synchronization.
     */
    private fun ensureAvatars(usernames: Collection<String>) {
        usernames.filter { requestedAvatars.add(it) }.forEach { username ->
            viewModelScope.launch {
                avatarRepository.getAvatar(username)?.let { bytes ->
                    avatars.value = avatars.value + (username to bytes)
                }
            }
        }
    }

    /** Updates the composer only when one is open; a no-op otherwise. */
    private fun MutableStateFlow<ComposerState?>.update(transform: (ComposerState) -> ComposerState) {
        value = value?.let(transform)
    }

    /** The raw messages poll for an open thread; null [messages] means the first poll is in flight. */
    private data class ThreadMessages(val username: String, val messages: List<PrivateMessage>?)
}
