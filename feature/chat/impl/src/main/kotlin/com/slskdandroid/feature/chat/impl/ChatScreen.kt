package com.slskdandroid.feature.chat.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.model.Conversation
import com.slskdandroid.core.model.PrivateMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ChatRoute(viewModel: ChatViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(uiState = uiState, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    uiState: ChatUiState,
    onAction: (ChatAction) -> Unit,
) {
    val thread = uiState.thread
    // In a thread, system back returns to the conversation list rather than leaving the tab.
    BackHandler(enabled = thread != null) { onAction(ChatAction.CloseConversation) }

    Scaffold(
        topBar = {
            if (thread != null) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onAction(ChatAction.CloseConversation) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(uiState.avatars[thread.username], size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(thread.username)
                        }
                    },
                )
            } else {
                TopAppBar(title = { Text("Chat") })
            }
        },
        bottomBar = {
            if (thread != null) {
                MessageInputBar(
                    draft = thread.draft,
                    sending = thread.sending,
                    onDraftChange = { onAction(ChatAction.DraftChanged(it)) },
                    onSend = { onAction(ChatAction.SendDraft) },
                )
            }
        },
        floatingActionButton = {
            if (thread == null) {
                FloatingActionButton(onClick = { onAction(ChatAction.StartNewChat) }) {
                    Icon(Icons.Filled.Add, contentDescription = "New message")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (thread != null) {
                ThreadContent(thread)
            } else {
                ConversationList(uiState.list, uiState.avatars, onAction)
            }
        }
    }

    uiState.composer?.let { composer ->
        NewMessageDialog(composer = composer, onAction = onAction)
    }
}

@Composable
private fun ConversationList(
    list: ListState,
    avatars: Map<String, ByteArray>,
    onAction: (ChatAction) -> Unit,
) {
    when (list) {
        ListState.Loading -> CenteredContent {
            CircularProgressIndicator()
            Text("Loading conversations…", style = MaterialTheme.typography.bodyLarge)
        }

        is ListState.Error -> CenteredContent {
            Text(
                list.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = { onAction(ChatAction.RetryList) }) { Text("Retry") }
        }

        is ListState.Loaded ->
            if (list.conversations.isEmpty()) {
                CenteredMessage("No conversations yet. Tap + to message someone.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list.conversations, key = { it.username }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            avatar = avatars[conversation.username],
                            onClick = { onAction(ChatAction.OpenConversation(conversation.username)) },
                        )
                    }
                }
            }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, avatar: ByteArray?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(avatar, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Text(
            conversation.username,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (conversation.hasUnread) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (conversation.unreadCount > 0) {
            Badge { Text(conversation.unreadCount.toString()) }
        }
    }
}

/** A circular peer avatar; falls back to a person glyph when [bytes] is null or undecodable. */
@Composable
private fun UserAvatar(bytes: ByteArray?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) { bytes?.let(::decodeAvatar) }
    Box(
        modifier = modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(size * 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadContent(thread: ThreadState) {
    if (thread.loading) {
        CenteredContent { CircularProgressIndicator() }
        return
    }
    if (thread.messages.isEmpty()) {
        CenteredMessage("No messages yet. Say hello to ${thread.username}.")
        return
    }
    val listState = rememberLazyListState()
    // Keep the newest message in view as the thread grows / a message is sent.
    LaunchedEffect(thread.messages.size) {
        if (thread.messages.isNotEmpty()) listState.animateScrollToItem(thread.messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // No stable key: slskd reuses id 0 for outgoing messages, so ids aren't unique.
        items(thread.messages) { message -> MessageBubble(message) }
    }
}

@Composable
private fun MessageBubble(message: PrivateMessage) {
    val outgoing = message.isOutgoing
    val bubbleColor =
        if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor =
        if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bubbleColor,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(message.message, style = MaterialTheme.typography.bodyMedium, color = textColor)
                message.timestampMillis?.let { millis ->
                    Text(
                        formatTime(millis),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    draft: String,
    sending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            val canSend = draft.isNotBlank() && !sending
            IconButton(onClick = onSend, enabled = canSend) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NewMessageDialog(composer: ComposerState, onAction: (ChatAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!composer.sending) onAction(ChatAction.ComposerDismiss) },
        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
        title = { Text("New message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = composer.username,
                    onValueChange = { onAction(ChatAction.ComposerUsernameChanged(it)) },
                    label = { Text("Username") },
                    singleLine = true,
                    readOnly = composer.usernameLocked,
                    enabled = !composer.usernameLocked,
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = composer.message,
                    onValueChange = { onAction(ChatAction.ComposerMessageChanged(it)) },
                    label = { Text("Message") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                composer.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAction(ChatAction.ComposerSubmit) },
                enabled = !composer.sending,
            ) {
                if (composer.sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Send")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(ChatAction.ComposerDismiss) },
                enabled = !composer.sending,
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun CenteredMessage(
    message: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** Decodes raw avatar bytes to a bitmap; returns null if the payload isn't a valid image. */
private fun decodeAvatar(bytes: ByteArray): Bitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun formatTime(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis))
