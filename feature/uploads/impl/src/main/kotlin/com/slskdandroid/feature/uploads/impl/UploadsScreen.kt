package com.slskdandroid.feature.uploads.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.model.Upload
import com.slskdandroid.core.model.UploadState
import java.util.Locale

@Composable
internal fun UploadsRoute(
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
    viewModel: UploadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UploadsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBrowseUser = onBrowseUser,
        onUserInfo = onUserInfo,
        onChatUser = onChatUser,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UploadsScreen(
    uiState: UploadsUiState,
    onAction: (UploadsAction) -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
) {
    // While selecting, system back clears the selection rather than leaving the screen.
    BackHandler(enabled = uiState.inSelectionMode) { onAction(UploadsAction.ClearSelection) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Uploads") }) },
        bottomBar = {
            // Hidden until something is selected; sits above the app's bottom navigation.
            if (uiState.inSelectionMode) {
                SelectionBar(
                    count = uiState.selectedIds.size,
                    onClear = { onAction(UploadsAction.ClearSelection) },
                    onRemove = { onAction(UploadsAction.RemoveSelected) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            BulkActionBar(onAction)

            when (uiState.loadState) {
                LoadState.Loading -> CenteredMessage("Loading uploads…")

                is LoadState.Error ->
                    CenteredMessage(uiState.loadState.message, MaterialTheme.colorScheme.error)

                LoadState.Loaded ->
                    if (uiState.users.isEmpty()) {
                        CenteredMessage("No uploads. Peers' requests for your shared files appear here.")
                    } else {
                        UploadsList(uiState, onAction, onBrowseUser, onUserInfo, onChatUser)
                    }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$count selected", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear") }
            Spacer(Modifier.width(8.dp))
            // Remove covers every state: with remove=true it cancels active transfers too.
            Button(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun BulkActionBar(onAction: (UploadsAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MenuButton(
            label = "Cancel",
            options = listOf(
                "All" to CancelFilter.All,
                "Queued" to CancelFilter.Queued,
                "In progress" to CancelFilter.InProgress,
            ),
            onSelect = { onAction(UploadsAction.BulkCancel(it)) },
            modifier = Modifier.weight(1f),
        )
        MenuButton(
            label = "Remove All",
            options = listOf(
                "Succeeded" to RemoveFilter.Succeeded,
                "Errored" to RemoveFilter.Errored,
                "Cancelled" to RemoveFilter.Cancelled,
                "All completed" to RemoveFilter.Completed,
            ),
            onSelect = { onAction(UploadsAction.BulkRemove(it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun <T> MenuButton(
    label: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun UploadsList(
    uiState: UploadsUiState,
    onAction: (UploadsAction) -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        uiState.users.forEach { user ->
            val collapsed = user.username in uiState.collapsedUsers
            item(key = "user-${user.username}") {
                PeerHeader(
                    username = user.username,
                    fileCount = user.fileCount,
                    collapsed = collapsed,
                    onToggle = { onAction(UploadsAction.ToggleCollapse(user.username)) },
                    onBrowseUser = onBrowseUser,
                    onUserInfo = onUserInfo,
                    onChatUser = onChatUser,
                    modifier = Modifier.animateItem(),
                )
            }
            if (!collapsed) {
                user.directories.forEach { dir ->
                    val dirCollapsed =
                        directoryKey(user.username, dir.directory) in uiState.collapsedDirectories
                    item(key = "dir-${user.username}-${dir.directory}") {
                        DirectoryHeader(
                            directory = dir.directory,
                            collapsed = dirCollapsed,
                            onToggle = {
                                onAction(
                                    UploadsAction.ToggleDirectoryCollapse(
                                        user.username,
                                        dir.directory,
                                    ),
                                )
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (!dirCollapsed) {
                        items(dir.uploads, key = { it.id }) { upload ->
                            UploadRow(
                                upload = upload,
                                selected = upload.id in uiState.selectedIds,
                                inSelectionMode = uiState.inSelectionMode,
                                onAction = onAction,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerHeader(
    username: String,
    fileCount: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = if (collapsed) "Expand $username" else "Collapse $username",
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "$username · $fileCount files",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        PeerOverflowMenu(username, onBrowseUser, onUserInfo, onChatUser)
    }
}

/** Per-peer overflow actions: open the peer's profile, browse their share, or message them. */
@Composable
private fun PeerOverflowMenu(
    username: String,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More actions for $username")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Info") },
                onClick = {
                    expanded = false
                    onUserInfo(username)
                },
            )
            DropdownMenuItem(
                text = { Text("Browse user") },
                onClick = {
                    expanded = false
                    onBrowseUser(username)
                },
            )
            DropdownMenuItem(
                text = { Text("Chat") },
                onClick = {
                    expanded = false
                    onChatUser(username)
                },
            )
        }
    }
}

@Composable
private fun DirectoryHeader(
    directory: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = if (collapsed) "Expand directory" else "Collapse directory",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        // No ellipsis: the full remote path scrolls horizontally so long paths stay readable.
        Text(
            directory.ifBlank { "(root)" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadRow(
    upload: Upload,
    selected: Boolean,
    inSelectionMode: Boolean,
    onAction: (UploadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (inSelectionMode) onAction(UploadsAction.ToggleSelection(upload.id))
                },
                onLongClick = { onAction(UploadsAction.StartSelection(upload.id)) },
            )
            .background(background)
            .padding(start = 28.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inSelectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onAction(UploadsAction.ToggleSelection(upload.id)) },
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    upload.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    upload.statusLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = stateColor(upload.state),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (upload.state == UploadState.InProgress) {
            Spacer(Modifier.height(6.dp))
            LinearWavyProgressIndicator(
                progress = { (upload.percentComplete / 100.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

@Composable
private fun stateColor(state: UploadState): Color = when (state) {
    UploadState.InProgress -> MaterialTheme.colorScheme.primary
    UploadState.Completed -> MaterialTheme.colorScheme.primary
    UploadState.Queued -> MaterialTheme.colorScheme.tertiary
    UploadState.Failed -> MaterialTheme.colorScheme.error
    UploadState.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** A compact, state-appropriate one-liner: state + size/progress/speed/queue position. */
private fun Upload.statusLine(): String = when (state) {
    UploadState.InProgress -> {
        val speed = if (averageSpeed > 0) " · ${formatBytes(averageSpeed.toLong())}/s" else ""
        "${formatBytes(bytesTransferred)} / ${formatBytes(sizeBytes)}$speed"
    }

    UploadState.Queued ->
        placeInQueue?.let { "Queued · #$it" } ?: "Queued · ${formatBytes(sizeBytes)}"

    UploadState.Completed -> "Sent · ${formatBytes(sizeBytes)}"

    UploadState.Failed -> {
        val reason = exception?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        "Failed · ${formatBytes(sizeBytes)}$reason"
    }

    UploadState.Unknown -> formatBytes(sizeBytes)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
