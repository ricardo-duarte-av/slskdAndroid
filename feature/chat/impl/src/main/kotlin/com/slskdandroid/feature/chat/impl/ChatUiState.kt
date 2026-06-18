package com.slskdandroid.feature.chat.impl

import com.slskdandroid.core.model.Conversation
import com.slskdandroid.core.model.PrivateMessage

/**
 * The Chat tab's single state. The tab is a self-contained two-view machine: the conversation
 * [list] is always present, an open [thread] overlays it when a conversation is selected, and the
 * new-message [composer] is a floating dialog shown on top of either.
 */
data class ChatUiState(
    val list: ListState = ListState.Loading,
    val thread: ThreadState? = null,
    val composer: ComposerState? = null,
    /** Avatar image bytes per username, populated as they load. Absent = none / not yet fetched. */
    val avatars: Map<String, ByteArray> = emptyMap(),
)

sealed interface ListState {
    data object Loading : ListState
    data class Error(val message: String) : ListState
    data class Loaded(val conversations: List<Conversation>) : ListState
}

/** An open conversation thread. [draft] is the message being typed; [sending] gates the send. */
data class ThreadState(
    val username: String,
    val messages: List<PrivateMessage> = emptyList(),
    val loading: Boolean = true,
    val draft: String = "",
    val sending: Boolean = false,
)

/**
 * The new-message composer dialog. [usernameLocked] is true when opened from a specific peer (the
 * Users/Search "Chat" action), where the recipient is fixed and only the message is requested.
 */
data class ComposerState(
    val username: String = "",
    val message: String = "",
    val usernameLocked: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
)

sealed interface ChatAction {
    /** Open the thread for [username] (from a list tap). */
    data class OpenConversation(val username: String) : ChatAction
    data object CloseConversation : ChatAction
    data class DraftChanged(val text: String) : ChatAction
    data object SendDraft : ChatAction

    /** Retry loading the conversation list after an error. */
    data object RetryList : ChatAction

    /** Open the new-message composer (the "+" action) with an empty recipient. */
    data object StartNewChat : ChatAction
    data class ComposerUsernameChanged(val username: String) : ChatAction
    data class ComposerMessageChanged(val message: String) : ChatAction
    data object ComposerSubmit : ChatAction
    data object ComposerDismiss : ChatAction
}
