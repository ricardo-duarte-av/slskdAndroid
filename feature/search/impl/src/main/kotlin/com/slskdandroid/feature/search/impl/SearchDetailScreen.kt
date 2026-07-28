package com.slskdandroid.feature.search.impl

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.designsystem.component.DepthCard
import com.slskdandroid.core.designsystem.component.qualityLabelLocalized
import com.slskdandroid.core.designsystem.component.formatDurationLocalized
import com.slskdandroid.core.designsystem.component.formatBytes
import com.slskdandroid.core.designsystem.component.formatBitRateLocalized
import com.slskdandroid.core.designsystem.component.asString
import com.slskdandroid.core.model.SearchResultFile
import kotlin.math.min

private const val PAGE_SIZE = 5

@Composable
internal fun SearchDetailRoute(
    onBack: () -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
    viewModel: SearchDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchDetailScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBack = onBack,
        onBrowseUser = onBrowseUser,
        onUserInfo = onUserInfo,
        onChatUser = onChatUser,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchDetailScreen(
    uiState: SearchDetailUiState,
    onAction: (SearchDetailAction) -> Unit,
    onBack: () -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.search_detail_back),
                        )
                    }
                },
                title = {
                    Text(
                        uiState.searchText.ifBlank { stringResource(R.string.search_detail_results) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
        bottomBar = {
            if (uiState.selectedCount > 0) {
                SelectionBar(
                    count = uiState.selectedCount,
                    sizeBytes = uiState.selectedSizeBytes,
                    onClear = { onAction(SearchDetailAction.ClearSelection) },
                    onDownload = { onAction(SearchDetailAction.DownloadSelected) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val phase = uiState.phase) {
                Phase.Loading -> CenteredMessage(stringResource(R.string.search_detail_loading))

                is Phase.Error -> CenteredMessage(phase.message.asString(), MaterialTheme.colorScheme.error)

                is Phase.Loaded -> {
                    if (!phase.isComplete) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    LoadedResults(phase, uiState.options, onAction, onBrowseUser, onUserInfo, onChatUser)
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    sizeBytes: Long,
    onClear: () -> Unit,
    onDownload: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    R.string.search_selection_summary,
                    pluralStringResource(R.plurals.search_selected_files, count, count),
                    formatBytes(sizeBytes),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text(stringResource(R.string.search_clear)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onDownload) { Text(stringResource(R.string.search_download_selected)) }
        }
    }
}

@Composable
private fun LoadedResults(
    phase: Phase.Loaded,
    options: SearchOptions,
    onAction: (SearchDetailAction) -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "options") { OptionsPanel(options, onAction) }

        if (phase.responses.isEmpty()) {
            item(key = "empty") {
                CenteredMessage(
                    stringResource(
                        if (phase.isComplete) {
                            R.string.search_detail_no_results
                        } else {
                            R.string.search_detail_searching
                        },
                    ),
                )
            }
        }

        // Each peer is a single card; its folders and files are cards nested within it (see
        // PeerCard). Keeping the peer as the lazy-item boundary preserves list virtualization
        // while giving the nested-card hierarchy.
        items(phase.responses, key = { "peer-${it.username}" }) { response ->
            PeerCard(
                response = response,
                onAction = onAction,
                onBrowseUser = onBrowseUser,
                onUserInfo = onUserInfo,
                onChatUser = onChatUser,
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "pager") {
            Pager(
                remainingCount = phase.remainingCount,
                filteredCount = phase.filteredCount,
                onShowMore = { onAction(SearchDetailAction.ShowMore) },
            )
        }
    }
}

/** A peer (depth 0): the outermost card. Holds the peer header and, when expanded, its folders. */
@Composable
private fun PeerCard(
    response: ShownResponse,
    onAction: (SearchDetailAction) -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DepthCard(
        depth = 0,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            PeerHeader(
                response = response,
                onToggle = { onAction(SearchDetailAction.TogglePeer(response.username)) },
                onBrowseUser = onBrowseUser,
                onUserInfo = onUserInfo,
                onChatUser = onChatUser,
            )
            if (!response.folded) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    response.directories.forEach { dir ->
                        DirectoryCard(response.username, dir, onAction)
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
    dir: ShownDirectory,
    onAction: (SearchDetailAction) -> Unit,
) {
    DepthCard(
        depth = 1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            DirectoryHeader(username, dir, onAction)
            if (!dir.collapsed) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    dir.files.forEach { shown ->
                        FileCard(username, shown, onAction)
                    }
                }
            }
        }
    }
}

/** A file (depth 2): the innermost bubble. */
@Composable
private fun FileCard(
    username: String,
    shown: ShownFile,
    onAction: (SearchDetailAction) -> Unit,
) {
    DepthCard(
        depth = 2,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FileRow(username = username, shown = shown, onAction = onAction)
    }
}

@Composable
private fun OptionsPanel(
    options: SearchOptions,
    onAction: (SearchDetailAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SortDropdown(options.sort, onSelect = { onAction(SearchDetailAction.SetSort(it)) })

        ToggleRow(stringResource(R.string.search_hide_locked), options.hideLocked) {
            onAction(SearchDetailAction.ToggleHideLocked)
        }
        ToggleRow(stringResource(R.string.search_hide_no_free_slots), options.hideNoFreeSlots) {
            onAction(SearchDetailAction.ToggleHideNoFreeSlots)
        }
        ToggleRow(stringResource(R.string.search_fold_results), options.foldResults) {
            onAction(SearchDetailAction.ToggleFold)
        }

        OutlinedTextField(
            value = options.filterText,
            onValueChange = { onAction(SearchDetailAction.SetFilter(it)) },
            label = { Text(stringResource(R.string.search_filter_label)) },
            placeholder = {
                Text(FILTER_PLACEHOLDER, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            },
            singleLine = true,
            trailingIcon = {
                if (options.filterText.isNotEmpty()) {
                    IconButton(onClick = { onAction(SearchDetailAction.ClearFilter) }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.search_clear_filter),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SortDropdown(sort: ResultSort, onSelect: (ResultSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(sort.labelRes))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ResultSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun PeerHeader(
    response: ShownResponse,
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
            imageVector = if (response.folded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(
                if (response.folded) R.string.search_expand else R.string.search_collapse,
            ),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        val slot = stringResource(
            if (response.hasFreeUploadSlot) R.string.search_peer_free_slot else R.string.search_peer_no_slot,
        )
        Text(
            stringResource(
                R.string.search_peer_summary,
                response.username,
                response.fileCount,
                slot,
                response.uploadSpeed / 1024,
                response.queueLength,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PeerOverflowMenu(response.username, onBrowseUser, onUserInfo, onChatUser)
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
                contentDescription = stringResource(R.string.search_more_actions_for, username),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_info)) },
                onClick = {
                    expanded = false
                    onUserInfo(username)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_browse_user)) },
                onClick = {
                    expanded = false
                    onBrowseUser(username)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_chat)) },
                onClick = {
                    expanded = false
                    onChatUser(username)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DirectoryHeader(
    username: String,
    dir: ShownDirectory,
    onAction: (SearchDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tap toggles the folder's collapse; long-press selects/deselects all its files.
    val selectAll = dir.selection != TriState.All
    val selectionLabel = stringResource(
        if (selectAll) R.string.search_select_all_in_folder else R.string.search_deselect_all_in_folder,
    )
    val setSelection = {
        onAction(
            SearchDetailAction.SetDirectorySelection(
                username = username,
                files = dir.files.map { it.file },
                selected = selectAll,
            ),
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onAction(SearchDetailAction.ToggleDirectoryCollapse(username, dir.directory)) },
                onClickLabel = stringResource(
                    if (dir.collapsed) R.string.search_expand_folder else R.string.search_collapse_folder,
                ),
                onLongClick = setSelection,
                onLongClickLabel = selectionLabel,
            )
            // Also expose the long-press as a named action, so it's reachable from TalkBack's
            // actions menu rather than only as an unlabelled double-tap-and-hold.
            .semantics {
                customActions = listOf(CustomAccessibilityAction(selectionLabel) { setSelection(); true })
            }
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (dir.collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(
                if (dir.collapsed) R.string.search_expand_directory else R.string.search_collapse_directory,
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        // No ellipsis: the full remote path scrolls horizontally so long paths stay readable.
        Text(
            dir.directory.ifBlank { stringResource(R.string.search_root_directory) },
            style = MaterialTheme.typography.labelMedium,
            color = if (dir.selection == TriState.None) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        )
        when {
            dir.expanding -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            !dir.expanded -> IconButton(
                onClick = { onAction(SearchDetailAction.ExpandDirectory(username, dir.directory)) },
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.search_directory_files),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    username: String,
    shown: ShownFile,
    onAction: (SearchDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = shown.file
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = shown.selected,
            onCheckedChange = { onAction(SearchDetailAction.ToggleFileSelection(username, file)) },
        )
        if (file.isLocked) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = stringResource(R.string.search_locked),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                file.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                fileMeta(file),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = { onAction(SearchDetailAction.Download(username, file)) },
            enabled = !file.isLocked,
        ) {
            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.search_download))
        }
    }
}

@Composable
private fun Pager(
    remainingCount: Int,
    filteredCount: Int,
    onShowMore: () -> Unit,
) {
    val hidden = stringResource(R.string.search_hidden_by_filters, filteredCount)
    when {
        remainingCount > 0 -> Button(
            onClick = onShowMore,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(
                stringResource(
                    R.string.search_show_more,
                    min(remainingCount, PAGE_SIZE),
                    remainingCount,
                    hidden,
                ),
            )
        }

        filteredCount > 0 -> Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(stringResource(R.string.search_all_shown, hidden))
        }
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** size · bitrate · quality · length · type, omitting parts slskd didn't report. */
@Composable
private fun fileMeta(file: SearchResultFile): String = buildList {
    add(formatBytes(file.sizeBytes))
    file.bitRate?.let { add(formatBitRateLocalized(it)) }
    qualityLabelLocalized(file.bitDepth, file.sampleRate)?.let { add(it) }
    file.lengthSeconds?.let { add(formatDurationLocalized(it)) }
    file.extension?.takeIf { it.isNotBlank() }?.let { add(it.trimStart('.').uppercase()) }
}.joinToString(" · ")



