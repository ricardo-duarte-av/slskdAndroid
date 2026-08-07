package com.slskdandroid.feature.chat.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.designsystem.component.ReadableWidth
import com.slskdandroid.core.designsystem.component.SettingsActionButton
import com.slskdandroid.core.designsystem.component.asString
import com.slskdandroid.core.designsystem.testing.SlskdTestTags
import com.slskdandroid.core.model.Conversation
import com.slskdandroid.core.model.PrivateMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun ChatRoute(onSettings: () -> Unit, viewModel: ChatViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(uiState = uiState, onAction = viewModel::onAction, onSettings = onSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    uiState: ChatUiState,
    onAction: (ChatAction) -> Unit,
    onSettings: () -> Unit,
) {
    val thread = uiState.thread
    // In a thread, system back returns to the conversation list rather than leaving the tab.
    BackHandler(enabled = thread != null) { onAction(ChatAction.CloseConversation) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (thread != null) {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { onAction(ChatAction.CloseConversation) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
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
                TopAppBar(
                    title = { Text(stringResource(R.string.chat_title)) },
                    actions = { SettingsActionButton(onSettings) },
                    scrollBehavior = scrollBehavior,
                )
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
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_new_message))
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (thread != null) {
                ThreadContent(thread, onAction)
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
            Text(stringResource(R.string.chat_loading), style = MaterialTheme.typography.bodyLarge)
        }

        is ListState.Error -> CenteredContent {
            Text(
                list.message.asString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = { onAction(ChatAction.RetryList) }) { Text(stringResource(R.string.chat_retry)) }
        }

        is ListState.Loaded ->
            if (list.conversations.isEmpty()) {
                CenteredMessage(stringResource(R.string.chat_empty))
            } else {
                ReadableWidth {
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
}

@Composable
private fun ConversationRow(conversation: Conversation, avatar: ByteArray?, onClick: () -> Unit) {
    // M3 ListItem: avatar in the leading slot, unread badge trailing. Replaces a hand-rolled
    // Row whose padding and type roles were approximations of the same spec.
    ListItem(
        leadingContent = { UserAvatar(avatar, size = 40.dp) },
        trailingContent = {
            if (conversation.unreadCount > 0) {
                Badge { Text(conversation.unreadCount.toString()) }
            }
        },
        modifier = Modifier
            .testTag(SlskdTestTags.CONVERSATION_ROW)
            .clickable(
                onClick = onClick,
                onClickLabel = stringResource(R.string.chat_open_conversation),
            ),
    ) {
        Text(
            conversation.username,
            fontWeight = if (conversation.hasUnread) FontWeight.Bold else FontWeight.Normal,
        )
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
private fun ThreadContent(thread: ThreadState, onAction: (ChatAction) -> Unit) {
    if (thread.loading) {
        CenteredContent { CircularProgressIndicator() }
        return
    }
    if (thread.messages.isEmpty()) {
        CenteredMessage(stringResource(R.string.chat_thread_empty, thread.username))
        return
    }
    val listState = rememberLazyListState()
    // Keep the newest message in view. The first positioning is an instant jump — animating from
    // the top through every message on open is what caused the janky, low-fps load; subsequent new
    // messages animate smoothly.
    var didInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(thread.messages.size) {
        if (thread.messages.isEmpty()) return@LaunchedEffect
        val target = thread.messages.lastIndex
        if (didInitialScroll) {
            listState.animateScrollToItem(target)
        } else {
            listState.scrollToItem(target)
            didInitialScroll = true
        }
    }
    val haptics = LocalHapticFeedback.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // slskd reuses id 0 for outgoing messages, so ids alone aren't unique. The thread is
        // append-only and re-fetched wholesale every 2s, so position + content identifies a
        // message well enough to stop every visible card recomposing on each poll.
        itemsIndexed(
            items = thread.messages,
            key = { index, message ->
                "$index\u0000${message.timestampMillis}\u0000${message.id}"
            },
        ) { _, message ->
            val sender = if (message.isOutgoing) stringResource(R.string.chat_sender_self) else message.username
            MessageCard(
                message = message,
                sender = sender,
                onReply = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAction(ChatAction.DraftChanged("[$sender] --> "))
                },
            )
        }
    }
}

/**
 * A full-width card for a single message: a header row with the sender and time, then the body.
 * Outgoing and incoming messages take distinct container colors from the Expressive palette.
 * Long-pressing seeds the input box with a reply prefix ([onReply]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageCard(message: PrivateMessage, sender: String, onReply: () -> Unit) {
    val outgoing = message.isOutgoing
    val containerColor =
        if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor =
        if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    // Lower-emphasis colour for the timestamp. Incoming cards sit on a surface container, so the
    // paired variant role applies; outgoing cards sit on primaryContainer, which has no "variant"
    // partner — there the smaller type scale carries the de-emphasis instead of a colour change.
    // (Previously both used contentColor.copy(alpha = 0.7f), which has no contrast guarantee.)
    val timestampColor =
        if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val replyLabel = stringResource(R.string.chat_reply_to, sender)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                // Tapping a message does nothing; the card is clickable only to host the
                // long-press ripple. onLongClickLabel is what TalkBack announces for the gesture.
                onClick = {},
                onLongClick = onReply,
                onLongClickLabel = replyLabel,
            )
            .semantics {
                // Don't advertise a tap action that does nothing, and surface the long-press as a
                // named action in TalkBack's actions menu rather than an unlabelled gesture.
                onClick(action = null)
                customActions = listOf(CustomAccessibilityAction(replyLabel) { onReply(); true })
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sender,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                )
                message.timestampMillis?.let { millis ->
                    Text(
                        formatTime(millis),
                        style = MaterialTheme.typography.labelSmall,
                        color = timestampColor,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(message.message, style = MaterialTheme.typography.bodyMedium, color = contentColor)
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
                placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
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
                        contentDescription = stringResource(R.string.chat_send),
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
        title = { Text(stringResource(R.string.chat_new_message)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = composer.username,
                    onValueChange = { onAction(ChatAction.ComposerUsernameChanged(it)) },
                    label = { Text(stringResource(R.string.chat_username_label)) },
                    singleLine = true,
                    readOnly = composer.usernameLocked,
                    enabled = !composer.usernameLocked,
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = composer.message,
                    onValueChange = { onAction(ChatAction.ComposerMessageChanged(it)) },
                    label = { Text(stringResource(R.string.chat_message_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                composer.error?.let {
                    Text(
                        it.asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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
                    Text(stringResource(R.string.chat_send))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(ChatAction.ComposerDismiss) },
                enabled = !composer.sending,
            ) { Text(stringResource(R.string.chat_cancel)) }
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

/**
 * Localized clock time. Was `ofPattern("HH:mm")`, which forced a 24-hour clock on every
 * locale and ignored the user's 12/24-hour system preference.
 */
private val timeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private fun formatTime(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis))
