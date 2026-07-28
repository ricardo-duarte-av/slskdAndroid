package com.slskdandroid.feature.downloads.impl

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.slskdandroid.core.model.Download
import com.slskdandroid.core.designsystem.component.DepthCard
import com.slskdandroid.core.designsystem.component.formatBytes
import com.slskdandroid.core.designsystem.component.ReadableWidth
import com.slskdandroid.core.designsystem.component.SettingsActionButton
import com.slskdandroid.core.designsystem.component.asString
import com.slskdandroid.core.designsystem.component.TransferItem
import com.slskdandroid.core.designsystem.component.TransferPhase
import com.slskdandroid.core.designsystem.component.TransferStatusLine
import com.slskdandroid.core.designsystem.component.nestedCardColor
import com.slskdandroid.core.designsystem.component.transferStatusOf
import com.slskdandroid.core.model.DownloadState
import kotlinx.coroutines.flow.Flow

@Composable
internal fun DownloadsRoute(
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Bulk actions report their outcome once, as a snackbar. Removals offer Undo, which re-queues
    // the cleared transfers — the same call Retry makes, so it genuinely restores them.
    BulkActionFeedback(viewModel.events, snackbarHostState, viewModel::onAction)

    DownloadsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBrowseUser = onBrowseUser,
        onUserInfo = onUserInfo,
        onChatUser = onChatUser,
        onSettings = onSettings,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Turns one-shot [DownloadsEvent]s into snackbars. Kept out of [DownloadsScreen] so that stays a
 * pure, previewable function of its state.
 */
@Composable
private fun BulkActionFeedback(
    events: Flow<DownloadsEvent>,
    snackbarHostState: SnackbarHostState,
    onAction: (DownloadsAction) -> Unit,
) {
    val resources = LocalContext.current.resources
    val undoLabel = stringResource(R.string.downloads_undo)
    LaunchedEffect(events) {
        events.collect { event ->
            val message = when (event) {
                is DownloadsEvent.Removed ->
                    resources.getQuantityString(R.plurals.downloads_removed, event.count, event.count)

                is DownloadsEvent.Cancelled ->
                    resources.getQuantityString(R.plurals.downloads_cancelled, event.count, event.count)

                is DownloadsEvent.Retried ->
                    resources.getQuantityString(R.plurals.downloads_retried, event.count, event.count)

                is DownloadsEvent.Failed ->
                    resources.getString(R.string.downloads_action_failed, event.failed, event.attempted)
            }
            // Undo only for removals, and only when there is something to re-queue.
            val undoable = event as? DownloadsEvent.Removed
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel.takeIf { undoable?.restorable?.isNotEmpty() == true },
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed && undoable != null) {
                onAction(DownloadsAction.UndoRemove(undoable.restorable))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsScreen(
    uiState: DownloadsUiState,
    onAction: (DownloadsAction) -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
    onSettings: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // While selecting, a system back press clears the selection rather than leaving the screen.
    BackHandler(enabled = uiState.inSelectionMode) { onAction(DownloadsAction.ClearSelection) }

    // One behaviour shared by both bars, so switching in and out of selection mode doesn't
    // reset the collapse state.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.inSelectionMode) {
                SelectionTopBar(
                    count = uiState.selectedIds.size,
                    onClear = { onAction(DownloadsAction.ClearSelection) },
                    onCancel = { onAction(DownloadsAction.CancelSelected) },
                    onRemove = { onAction(DownloadsAction.RemoveSelected) },
                    scrollBehavior = scrollBehavior,
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.downloads_title)) },
                    actions = { SettingsActionButton(onSettings) },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!uiState.inSelectionMode) {
                BulkActionBar(onAction)
            }

            when (uiState.loadState) {
                LoadState.Loading -> CenteredMessage(stringResource(R.string.downloads_loading))

                is LoadState.Error ->
                    CenteredMessage(uiState.loadState.message.asString(), MaterialTheme.colorScheme.error)

                LoadState.Loaded ->
                    if (uiState.users.isEmpty()) {
                        CenteredMessage(stringResource(R.string.downloads_empty))
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
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.downloads_clear_selection))
            }
        },
        title = { Text(stringResource(R.string.downloads_selected_count, count)) },
        actions = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.downloads_cancel)) }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.downloads_remove)) }
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
            label = stringResource(R.string.downloads_bulk_retry),
            options = listOf(
                stringResource(R.string.downloads_filter_errored) to RetryFilter.Errored,
                stringResource(R.string.downloads_filter_cancelled) to RetryFilter.Cancelled,
                stringResource(R.string.downloads_filter_all) to RetryFilter.All,
            ),
            onSelect = { onAction(DownloadsAction.BulkRetry(it)) },
            modifier = Modifier.weight(1f),
        )
        MenuButton(
            label = stringResource(R.string.downloads_cancel),
            options = listOf(
                stringResource(R.string.downloads_filter_all) to CancelFilter.All,
                stringResource(R.string.downloads_filter_queued) to CancelFilter.Queued,
                stringResource(R.string.downloads_filter_in_progress) to CancelFilter.InProgress,
            ),
            onSelect = { onAction(DownloadsAction.BulkCancel(it)) },
            modifier = Modifier.weight(1f),
        )
        MenuButton(
            label = stringResource(R.string.downloads_remove),
            options = listOf(
                stringResource(R.string.downloads_filter_succeeded) to RemoveFilter.Succeeded,
                stringResource(R.string.downloads_filter_errored) to RemoveFilter.Errored,
                stringResource(R.string.downloads_filter_cancelled) to RemoveFilter.Cancelled,
                stringResource(R.string.downloads_filter_all_completed) to RemoveFilter.Completed,
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
    // Each peer is a nested card (peer → directory → file), matching Search and Uploads. The peer
    // is the lazy-item boundary, so list virtualization is preserved.
    ReadableWidth {
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
}

/** A peer (depth 0): the outermost card. Holds the peer header and, when expanded, its folders. */
@Composable
private fun UserCard(
    user: UserDownloads,
    uiState: DownloadsUiState,
    onAction: (DownloadsAction) -> Unit,
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
                onToggle = { onAction(DownloadsAction.ToggleCollapse(user.username)) },
                onBrowseUser = onBrowseUser,
                onUserInfo = onUserInfo,
                onChatUser = onChatUser,
            )
            TransferStatusLine(
                status = transferStatusOf(
                    user.directories.flatMap { it.downloads }.map { it.toTransferItem() },
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
    dir: DirectoryDownloads,
    uiState: DownloadsUiState,
    onAction: (DownloadsAction) -> Unit,
) {
    val dirCollapsed = directoryKey(username, dir.directory) in uiState.collapsedDirectories
    DepthCard(depth = 1, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.animateContentSize()) {
            DirectoryHeader(
                directory = dir.directory,
                collapsed = dirCollapsed,
                onToggle = { onAction(DownloadsAction.ToggleDirectoryCollapse(username, dir.directory)) },
            )
            TransferStatusLine(
                status = transferStatusOf(dir.downloads.map { it.toTransferItem() }),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
            )
            if (!dirCollapsed) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    dir.downloads.forEach { download ->
                        DownloadCard(
                            download = download,
                            selected = download.id in uiState.selectedIds,
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
private fun DownloadCard(
    download: Download,
    selected: Boolean,
    inSelectionMode: Boolean,
    onAction: (DownloadsAction) -> Unit,
) {
    DepthCard(
        depth = 2,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else nestedCardColor(2),
    ) {
        DownloadRow(download, selected, inSelectionMode, onAction)
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
            contentDescription = stringResource(
                if (collapsed) R.string.downloads_expand_user else R.string.downloads_collapse_user,
                username,
            ),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(
                R.string.downloads_peer_summary,
                username,
                pluralStringResource(R.plurals.downloads_peer_files, fileCount, fileCount),
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
                contentDescription = stringResource(R.string.downloads_more_actions_for, username),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.downloads_info)) },
                onClick = {
                    expanded = false
                    onUserInfo(username)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.downloads_browse_user)) },
                onClick = {
                    expanded = false
                    onBrowseUser(username)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.downloads_chat)) },
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
            contentDescription = stringResource(
                if (collapsed) R.string.downloads_expand_directory else R.string.downloads_collapse_directory,
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
private fun DownloadRow(
    download: Download,
    selected: Boolean,
    inSelectionMode: Boolean,
    onAction: (DownloadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectLabel = stringResource(R.string.downloads_select_transfer)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (inSelectionMode) onAction(DownloadsAction.ToggleSelection(download.id))
                },
                onClickLabel = if (inSelectionMode) stringResource(R.string.downloads_toggle_selection) else null,
                onLongClick = { onAction(DownloadsAction.StartSelection(download.id)) },
                onLongClickLabel = selectLabel,
            )
            // Selection mode is otherwise only reachable by long-press; name it as an action so
            // TalkBack can offer it from the actions menu.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(selectLabel) {
                        onAction(DownloadsAction.StartSelection(download.id)); true
                    },
                )
            }
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
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

private fun Download.toTransferItem(): TransferItem = TransferItem(state.toTransferPhase(), percentComplete)

private fun DownloadState.toTransferPhase(): TransferPhase = when (this) {
    DownloadState.Queued -> TransferPhase.Queued
    DownloadState.InProgress -> TransferPhase.InProgress
    DownloadState.Completed -> TransferPhase.Completed
    DownloadState.Failed -> TransferPhase.Failed
    DownloadState.Unknown -> TransferPhase.Unknown
}

/**
 * A compact, state-appropriate one-liner: state label + size/progress/speed/queue position.
 * Composable so each state's phrasing is a localized resource; the byte sizes it interpolates are
 * pre-formatted by [formatBytes] (unit symbols KB/MB/GB/TB are not translated).
 */
@Composable
private fun Download.statusLine(): String = when (state) {
    DownloadState.InProgress ->
        if (averageSpeed > 0) {
            stringResource(
                R.string.downloads_status_in_progress_speed,
                formatBytes(bytesTransferred),
                formatBytes(sizeBytes),
                formatBytes(averageSpeed.toLong()),
            )
        } else {
            stringResource(
                R.string.downloads_status_in_progress,
                formatBytes(bytesTransferred),
                formatBytes(sizeBytes),
            )
        }

    DownloadState.Queued -> placeInQueue
        ?.let { stringResource(R.string.downloads_status_queued_position, it) }
        ?: stringResource(R.string.downloads_status_queued, formatBytes(sizeBytes))

    DownloadState.Completed -> stringResource(R.string.downloads_status_done, formatBytes(sizeBytes))

    DownloadState.Failed -> exception?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.downloads_status_failed_reason, formatBytes(sizeBytes), it) }
        ?: stringResource(R.string.downloads_status_failed, formatBytes(sizeBytes))

    DownloadState.Unknown -> formatBytes(sizeBytes)
}


/** Announced by TalkBack for the long-press that enters multi-select. */

