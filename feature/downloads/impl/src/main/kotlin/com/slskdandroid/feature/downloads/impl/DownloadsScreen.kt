package com.slskdandroid.feature.downloads.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
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
import com.slskdandroid.core.model.Download
import com.slskdandroid.core.model.DownloadState
import java.util.Locale

@Composable
internal fun DownloadsRoute(
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBrowseUser = onBrowseUser,
        onUserInfo = onUserInfo,
        onChatUser = onChatUser,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsScreen(
    uiState: DownloadsUiState,
    onAction: (DownloadsAction) -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
) {
    // While selecting, a system back press clears the selection rather than leaving the screen.
    BackHandler(enabled = uiState.inSelectionMode) { onAction(DownloadsAction.ClearSelection) }

    Scaffold(
        topBar = {
            if (uiState.inSelectionMode) {
                SelectionTopBar(
                    count = uiState.selectedIds.size,
                    onClear = { onAction(DownloadsAction.ClearSelection) },
                    onCancel = { onAction(DownloadsAction.CancelSelected) },
                    onRemove = { onAction(DownloadsAction.RemoveSelected) },
                )
            } else {
                TopAppBar(title = { Text("Downloads") })
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!uiState.inSelectionMode) {
                BulkActionBar(onAction)
            }

            when (uiState.loadState) {
                LoadState.Loading -> CenteredMessage("Loading downloads…")

                is LoadState.Error ->
                    CenteredMessage(uiState.loadState.message, MaterialTheme.colorScheme.error)

                LoadState.Loaded ->
                    if (uiState.users.isEmpty()) {
                        CenteredMessage("No downloads yet. Queue files from Search.")
                    } else {
                        DownloadsList(uiState, onAction, onBrowseUser, onUserInfo, onChatUser)
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
            }
        },
        title = { Text("$count selected") },
        actions = {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = onRemove) { Text("Remove") }
        },
    )
}

@Composable
private fun BulkActionBar(onAction: (DownloadsAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MenuButton(
            label = "Retry",
            options = listOf(
                "Errored" to RetryFilter.Errored,
                "Cancelled" to RetryFilter.Cancelled,
                "All" to RetryFilter.All,
            ),
            onSelect = { onAction(DownloadsAction.BulkRetry(it)) },
            modifier = Modifier.weight(1f),
        )
        MenuButton(
            label = "Cancel",
            options = listOf(
                "All" to CancelFilter.All,
                "Queued" to CancelFilter.Queued,
                "In progress" to CancelFilter.InProgress,
            ),
            onSelect = { onAction(DownloadsAction.BulkCancel(it)) },
            modifier = Modifier.weight(1f),
        )
        MenuButton(
            label = "Remove",
            options = listOf(
                "Succeeded" to RemoveFilter.Succeeded,
                "Errored" to RemoveFilter.Errored,
                "Cancelled" to RemoveFilter.Cancelled,
                "All completed" to RemoveFilter.Completed,
            ),
            onSelect = { onAction(DownloadsAction.BulkRemove(it)) },
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
private fun DownloadsList(
    uiState: DownloadsUiState,
    onAction: (DownloadsAction) -> Unit,
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
                    onToggle = { onAction(DownloadsAction.ToggleCollapse(user.username)) },
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
                                    DownloadsAction.ToggleDirectoryCollapse(
                                        user.username,
                                        dir.directory,
                                    ),
                                )
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (!dirCollapsed) {
                        items(dir.downloads, key = { it.id }) { download ->
                            DownloadRow(
                                download = download,
                                selected = download.id in uiState.selectedIds,
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
            imageVector = if (collapsed) {
                Icons.Filled.KeyboardArrowDown
            } else {
                Icons.Filled.KeyboardArrowUp
            },
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
            imageVector = if (collapsed) {
                Icons.Filled.KeyboardArrowDown
            } else {
                Icons.Filled.KeyboardArrowUp
            },
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
private fun DownloadRow(
    download: Download,
    selected: Boolean,
    inSelectionMode: Boolean,
    onAction: (DownloadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (inSelectionMode) onAction(DownloadsAction.ToggleSelection(download.id))
                },
                onLongClick = { onAction(DownloadsAction.StartSelection(download.id)) },
            )
            .background(background)
            .padding(start = 28.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inSelectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onAction(DownloadsAction.ToggleSelection(download.id)) },
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    download.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    download.statusLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = stateColor(download.state),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (download.state == DownloadState.InProgress) {
            Spacer(Modifier.height(6.dp))
            LinearWavyProgressIndicator(
                progress = { (download.percentComplete / 100.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

@Composable
private fun stateColor(state: DownloadState): Color = when (state) {
    DownloadState.InProgress -> MaterialTheme.colorScheme.primary
    DownloadState.Completed -> MaterialTheme.colorScheme.primary
    DownloadState.Queued -> MaterialTheme.colorScheme.tertiary
    DownloadState.Failed -> MaterialTheme.colorScheme.error
    DownloadState.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** A compact, state-appropriate one-liner: state label + size/progress/speed/queue position. */
private fun Download.statusLine(): String = when (state) {
    DownloadState.InProgress -> {
        val speed = if (averageSpeed > 0) " · ${formatBytes(averageSpeed.toLong())}/s" else ""
        "${formatBytes(bytesTransferred)} / ${formatBytes(sizeBytes)}$speed"
    }

    DownloadState.Queued ->
        placeInQueue?.let { "Queued · #$it" } ?: "Queued · ${formatBytes(sizeBytes)}"

    DownloadState.Completed -> "Done · ${formatBytes(sizeBytes)}"

    DownloadState.Failed -> {
        val reason = exception?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        "Failed · ${formatBytes(sizeBytes)}$reason"
    }

    DownloadState.Unknown -> formatBytes(sizeBytes)
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
