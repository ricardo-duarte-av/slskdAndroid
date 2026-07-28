package com.slskdandroid.feature.uploads.impl

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.designsystem.component.DepthCard
import com.slskdandroid.core.designsystem.component.formatBytes
import com.slskdandroid.core.designsystem.component.SettingsActionButton
import com.slskdandroid.core.designsystem.component.asString
import com.slskdandroid.core.designsystem.component.TransferItem
import com.slskdandroid.core.designsystem.component.TransferPhase
import com.slskdandroid.core.designsystem.component.TransferStatusLine
import com.slskdandroid.core.designsystem.component.nestedCardColor
import com.slskdandroid.core.designsystem.component.transferStatusOf
import com.slskdandroid.core.model.Upload
import com.slskdandroid.core.model.UploadState

@Composable
internal fun UploadsRoute(
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: UploadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UploadsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBrowseUser = onBrowseUser,
        onUserInfo = onUserInfo,
        onChatUser = onChatUser,
        onSettings = onSettings,
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
    onSettings: () -> Unit,
) {
    // While selecting, system back clears the selection rather than leaving the screen.
    BackHandler(enabled = uiState.inSelectionMode) { onAction(UploadsAction.ClearSelection) }

    // M3 expects a scroll behaviour on app bars over scrolling content; without one the

    // bar is a static block that never yields vertical space.

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()


    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.uploads_title)) },
                actions = { SettingsActionButton(onSettings) },
                scrollBehavior = scrollBehavior,
            )
        },
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
                LoadState.Loading -> CenteredMessage(stringResource(R.string.uploads_loading))

                is LoadState.Error ->
                    CenteredMessage(uiState.loadState.message.asString(), MaterialTheme.colorScheme.error)

                LoadState.Loaded ->
                    if (uiState.users.isEmpty()) {
                        CenteredMessage(stringResource(R.string.uploads_empty))
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
            Text(
                stringResource(R.string.uploads_selected_count, count),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text(stringResource(R.string.uploads_clear)) }
            Spacer(Modifier.width(8.dp))
            // Remove covers every state: with remove=true it cancels active transfers too.
            Button(onClick = onRemove) { Text(stringResource(R.string.uploads_remove)) }
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
            label = stringResource(R.string.uploads_bulk_cancel),
            options = listOf(
                stringResource(R.string.uploads_filter_all) to CancelFilter.All,
                stringResource(R.string.uploads_filter_queued) to CancelFilter.Queued,
                stringResource(R.string.uploads_filter_in_progress) to CancelFilter.InProgress,
            ),
            onSelect = { onAction(UploadsAction.BulkCancel(it)) },
            modifier = Modifier.weight(1f),
        )
        MenuButton(
            label = stringResource(R.string.uploads_bulk_remove_all),
            options = listOf(
                stringResource(R.string.uploads_filter_succeeded) to RemoveFilter.Succeeded,
                stringResource(R.string.uploads_filter_errored) to RemoveFilter.Errored,
                stringResource(R.string.uploads_filter_cancelled) to RemoveFilter.Cancelled,
                stringResource(R.string.uploads_filter_all_completed) to RemoveFilter.Completed,
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
    // Each peer is a nested card (peer → directory → file), matching Search and Downloads. The peer
    // is the lazy-item boundary, so list virtualization is preserved.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uiState.users, key = { "user-${it.username}" }) { user ->
            UserCard(
                user = user,
                uiState = uiState,
                onAction = onAction,
                onBrowseUser = onBrowseUser,
                onUserInfo = onUserInfo,
                onChatUser = onChatUser,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** A peer (depth 0): the outermost card. Holds the peer header and, when expanded, its folders. */
@Composable
private fun UserCard(
    user: UserUploads,
    uiState: UploadsUiState,
    onAction: (UploadsAction) -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapsed = user.username in uiState.collapsedUsers
    DepthCard(depth = 0, modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.animateContentSize()) {
            PeerHeader(
                username = user.username,
                fileCount = user.fileCount,
                collapsed = collapsed,
                onToggle = { onAction(UploadsAction.ToggleCollapse(user.username)) },
                onBrowseUser = onBrowseUser,
                onUserInfo = onUserInfo,
                onChatUser = onChatUser,
            )
            TransferStatusLine(
                status = transferStatusOf(
                    user.directories.flatMap { it.uploads }.map { it.toTransferItem() },
                ),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
            if (!collapsed) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    user.directories.forEach { dir ->
                        DirectoryCard(user.username, dir, uiState, onAction)
                    }
                }
            }
        }
    }
}

/** A folder (depth 1): a card nested inside its peer. Holds the folder header and its files. */
@Composable
private fun DirectoryCard(
    username: String,
    dir: DirectoryUploads,
    uiState: UploadsUiState,
    onAction: (UploadsAction) -> Unit,
) {
    val dirCollapsed = directoryKey(username, dir.directory) in uiState.collapsedDirectories
    DepthCard(depth = 1, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.animateContentSize()) {
            DirectoryHeader(
                directory = dir.directory,
                collapsed = dirCollapsed,
                onToggle = { onAction(UploadsAction.ToggleDirectoryCollapse(username, dir.directory)) },
            )
            TransferStatusLine(
                status = transferStatusOf(dir.uploads.map { it.toTransferItem() }),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
            )
            if (!dirCollapsed) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    dir.uploads.forEach { upload ->
                        UploadCard(
                            upload = upload,
                            selected = upload.id in uiState.selectedIds,
                            inSelectionMode = uiState.inSelectionMode,
                            onAction = onAction,
                        )
                    }
                }
            }
        }
    }
}

/** A transfer (depth 2): the innermost bubble; the tonal color yields to a highlight when selected. */
@Composable
private fun UploadCard(
    upload: Upload,
    selected: Boolean,
    inSelectionMode: Boolean,
    onAction: (UploadsAction) -> Unit,
) {
    DepthCard(
        depth = 2,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else nestedCardColor(2),
    ) {
        UploadRow(upload, selected, inSelectionMode, onAction)
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
            contentDescription = stringResource(
                if (collapsed) R.string.uploads_expand_user else R.string.uploads_collapse_user,
                username,
            ),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(
                R.string.uploads_peer_summary,
                username,
                pluralStringResource(R.plurals.uploads_peer_files, fileCount, fileCount),
            ),
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
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.uploads_more_actions_for, username),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.uploads_info)) },
                onClick = {
                    expanded = false
                    onUserInfo(username)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.uploads_browse_user)) },
                onClick = {
                    expanded = false
                    onBrowseUser(username)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.uploads_chat)) },
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
            contentDescription = stringResource(
                if (collapsed) R.string.uploads_expand_directory else R.string.uploads_collapse_directory,
            ),
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
    val selectLabel = stringResource(R.string.uploads_select_transfer)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (inSelectionMode) onAction(UploadsAction.ToggleSelection(upload.id))
                },
                onClickLabel = if (inSelectionMode) stringResource(R.string.uploads_toggle_selection) else null,
                onLongClick = { onAction(UploadsAction.StartSelection(upload.id)) },
                onLongClickLabel = selectLabel,
            )
            // Selection mode is otherwise only reachable by long-press; name it as an action so
            // TalkBack can offer it from the actions menu.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(selectLabel) {
                        onAction(UploadsAction.StartSelection(upload.id)); true
                    },
                )
            }
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
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

private fun Upload.toTransferItem(): TransferItem = TransferItem(state.toTransferPhase(), percentComplete)

private fun UploadState.toTransferPhase(): TransferPhase = when (this) {
    UploadState.Queued -> TransferPhase.Queued
    UploadState.InProgress -> TransferPhase.InProgress
    UploadState.Completed -> TransferPhase.Completed
    UploadState.Failed -> TransferPhase.Failed
    UploadState.Unknown -> TransferPhase.Unknown
}

/** A compact, state-appropriate one-liner: state + size/progress/speed/queue position. */
@Composable
private fun Upload.statusLine(): String = when (state) {
    UploadState.InProgress ->
        if (averageSpeed > 0) {
            stringResource(
                R.string.uploads_status_in_progress_speed,
                formatBytes(bytesTransferred),
                formatBytes(sizeBytes),
                formatBytes(averageSpeed.toLong()),
            )
        } else {
            stringResource(
                R.string.uploads_status_in_progress,
                formatBytes(bytesTransferred),
                formatBytes(sizeBytes),
            )
        }

    UploadState.Queued -> placeInQueue
        ?.let { stringResource(R.string.uploads_status_queued_position, it) }
        ?: stringResource(R.string.uploads_status_queued, formatBytes(sizeBytes))

    UploadState.Completed -> stringResource(R.string.uploads_status_sent, formatBytes(sizeBytes))

    UploadState.Failed -> exception?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.uploads_status_failed_reason, formatBytes(sizeBytes), it) }
        ?: stringResource(R.string.uploads_status_failed, formatBytes(sizeBytes))

    UploadState.Unknown -> formatBytes(sizeBytes)
}



