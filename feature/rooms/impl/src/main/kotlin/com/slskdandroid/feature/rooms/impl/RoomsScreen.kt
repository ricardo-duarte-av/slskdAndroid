package com.slskdandroid.feature.rooms.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.model.AvailableRoom
import com.slskdandroid.core.model.RoomMessage
import com.slskdandroid.core.model.RoomUser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun RoomsRoute(
    onUserInfo: (String) -> Unit,
    viewModel: RoomsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RoomsScreen(uiState = uiState, onAction = viewModel::onAction, onUserInfo = onUserInfo)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomsScreen(
    uiState: RoomsUiState,
    onAction: (RoomsAction) -> Unit,
    onUserInfo: (String) -> Unit,
) {
    val open = uiState.open
    val search = uiState.search

    // Back unwinds search → room → list, one level at a time.
    BackHandler(enabled = search != null) { onAction(RoomsAction.CloseSearch) }
    BackHandler(enabled = search == null && open != null) { onAction(RoomsAction.CloseRoom) }

    Scaffold(
        topBar = {
            when {
                search != null -> SimpleTopBar("Find rooms") { onAction(RoomsAction.CloseSearch) }
                open != null -> RoomTopBar(open, onAction)
                else -> TopAppBar(title = { Text("Rooms") })
            }
        },
        bottomBar = {
            if (search == null && open != null) {
                MessageInputBar(
                    draft = open.draft,
                    sending = open.sending,
                    onDraftChange = { onAction(RoomsAction.DraftChanged(it)) },
                    onSend = { onAction(RoomsAction.SendMessage) },
                )
            }
        },
        floatingActionButton = {
            if (search == null && open == null) {
                FloatingActionButton(onClick = { onAction(RoomsAction.OpenSearch) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Find rooms")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                search != null -> SearchContent(search, onAction)
                open != null -> RoomContent(open, onUserInfo)
                else -> RoomList(uiState.list, onAction)
            }
        }
    }

    // The member list is opt-in (toggled from the room top bar), shown as a modal sheet.
    if (open != null && open.usersVisible) {
        MembersSheet(
            room = open,
            onDismiss = { onAction(RoomsAction.ToggleUsers) },
            onMemberClick = { username ->
                onAction(RoomsAction.ToggleUsers)
                onUserInfo(username)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        title = { Text(title) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomTopBar(open: OpenRoom, onAction: (RoomsAction) -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = { onAction(RoomsAction.CloseRoom) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        title = { Text(open.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            IconButton(onClick = { onAction(RoomsAction.ToggleUsers) }) {
                Icon(Icons.Filled.Group, contentDescription = "Show members")
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Leave room") },
                        onClick = {
                            menu = false
                            onAction(RoomsAction.LeaveRoom(open.name))
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun RoomList(list: ListState, onAction: (RoomsAction) -> Unit) {
    when (list) {
        ListState.Loading -> CenteredContent {
            CircularProgressIndicator()
            Text("Loading rooms…", style = MaterialTheme.typography.bodyLarge)
        }

        is ListState.Error -> CenteredContent {
            Text(list.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            Button(onClick = { onAction(RoomsAction.RetryList) }) { Text("Retry") }
        }

        is ListState.Loaded ->
            if (list.rooms.isEmpty()) {
                CenteredMessage("You haven't joined any rooms. Tap search to find some.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list.rooms, key = { it }) { room ->
                        RoomRow(
                            name = room,
                            onOpen = { onAction(RoomsAction.OpenRoom(room)) },
                            onLeave = { onAction(RoomsAction.LeaveRoom(room)) },
                        )
                    }
                }
            }
    }
}

@Composable
private fun RoomRow(name: String, onOpen: () -> Unit, onLeave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More actions for $name")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Leave room") },
                    onClick = {
                        menu = false
                        onLeave()
                    },
                )
            }
        }
    }
}

@Composable
private fun RoomContent(open: OpenRoom, onUserInfo: (String) -> Unit) {
    if (open.loading && open.messages.isEmpty()) {
        CenteredContent { CircularProgressIndicator() }
        return
    }
    if (open.messages.isEmpty()) {
        CenteredMessage("No messages yet. Be the first to say something.")
        return
    }
    // Sender → country, for the per-message flag (drawn from the member snapshot).
    val countries = remember(open.users) {
        open.users.orEmpty().associate { it.username to it.countryCode }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(open.messages.size) {
        if (open.messages.isNotEmpty()) listState.animateScrollToItem(open.messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // No stable key: room messages have no id and senders/timestamps can repeat.
        items(open.messages) { message ->
            MessageItem(
                message = message,
                countryCode = countries[message.username],
                onSenderClick = { onUserInfo(message.username) },
            )
        }
    }
}

@Composable
private fun MessageItem(message: RoomMessage, countryCode: String?, onSenderClick: () -> Unit) {
    val nameColor =
        if (message.isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tapping the sender (flag + name) opens their profile in the Users tab.
            Row(
                modifier = Modifier.weight(1f, fill = false).clickable(onClick = onSenderClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                countryFlag(countryCode)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    message.username,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            message.timestampMillis?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(message.message, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersSheet(room: OpenRoom, onDismiss: () -> Unit, onMemberClick: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val users = room.users
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                if (users == null) "Members" else "Members · ${users.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            when {
                users == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                users.isEmpty() -> CenteredMessage("No members.")

                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(users, key = { it.username }) { user ->
                        MemberRow(user, onClick = { onMemberClick(user.username) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(user: RoomUser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(countryFlag(user.countryCode) ?: "🏳️", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        Text(user.username, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            "${user.fileCount} files",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchContent(search: SearchState, onAction: (RoomsAction) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search.query,
            onValueChange = { onAction(RoomsAction.SearchQueryChanged(it)) },
            label = { Text("Filter rooms") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        when (val phase = search.phase) {
            SearchPhase.Loading -> CenteredContent { CircularProgressIndicator() }

            is SearchPhase.Error -> CenteredContent {
                Text(phase.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { onAction(RoomsAction.RetrySearch) }) { Text("Retry") }
            }

            is SearchPhase.Loaded -> {
                val filtered = remember(phase.rooms, search.query) {
                    phase.rooms
                        .filter { it.name.contains(search.query, ignoreCase = true) }
                        .sortedByDescending { it.userCount }
                }
                if (filtered.isEmpty()) {
                    CenteredMessage("No rooms match.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.name }) { room ->
                            AvailableRoomRow(
                                room = room,
                                joining = room.name in search.joining,
                                onJoin = { onAction(RoomsAction.JoinRoom(room.name)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableRoomRow(room: AvailableRoom, joining: Boolean, onJoin: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (room.isPrivate) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Private",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    room.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${room.userCount} users",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (joining) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onJoin) { Text("Join") }
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
                        tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
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

/** ISO 3166-1 alpha-2 → flag emoji (two regional-indicator code points), or null if invalid. */
private fun countryFlag(code: String?): String? {
    val cc = code?.uppercase() ?: return null
    if (cc.length != 2 || cc.any { it !in 'A'..'Z' }) return null
    val first = 0x1F1E6 + (cc[0] - 'A')
    val second = 0x1F1E6 + (cc[1] - 'A')
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun formatTime(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis))
