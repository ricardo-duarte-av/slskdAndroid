package com.slskdandroid.feature.search.impl

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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.model.SearchResultFile
import kotlin.math.min

private const val PAGE_SIZE = 5

@Composable
internal fun SearchDetailRoute(
    onBack: () -> Unit,
    onBrowseUser: (String) -> Unit,
    viewModel: SearchDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchDetailScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBack = onBack,
        onBrowseUser = onBrowseUser,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchDetailScreen(
    uiState: SearchDetailUiState,
    onAction: (SearchDetailAction) -> Unit,
    onBack: () -> Unit,
    onBrowseUser: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        uiState.searchText.ifBlank { "Results" },
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
                Phase.Loading -> CenteredMessage("Loading results…")

                is Phase.Error -> CenteredMessage(phase.message, MaterialTheme.colorScheme.error)

                is Phase.Loaded -> {
                    if (!phase.isComplete) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    LoadedResults(phase, uiState.options, onAction, onBrowseUser)
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
                "$count file${if (count == 1) "" else "s"} · ${formatBytes(sizeBytes)}",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onDownload) { Text("Download selected") }
        }
    }
}

@Composable
private fun LoadedResults(
    phase: Phase.Loaded,
    options: SearchOptions,
    onAction: (SearchDetailAction) -> Unit,
    onBrowseUser: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "options") { OptionsPanel(options, onAction) }

        if (phase.responses.isEmpty()) {
            item(key = "empty") {
                CenteredMessage(if (phase.isComplete) "No matching results." else "Searching…")
            }
        }

        phase.responses.forEach { response ->
            item(key = "peer-${response.username}") {
                PeerHeader(
                    response = response,
                    onToggle = { onAction(SearchDetailAction.TogglePeer(response.username)) },
                    onBrowseUser = onBrowseUser,
                    modifier = Modifier.animateItem(),
                )
            }
            if (!response.folded) {
                response.directories.forEach { dir ->
                    item(key = "dir-${response.username}-${dir.directory}") {
                        DirectoryHeader(response.username, dir, onAction, Modifier.animateItem())
                    }
                    if (!dir.collapsed) {
                        items(dir.files, key = { "${response.username}-${it.file.filename}" }) { shown ->
                            FileRow(
                                username = response.username,
                                shown = shown,
                                onAction = onAction,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
            item(key = "div-${response.username}") { HorizontalDivider(Modifier.animateItem()) }
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

        ToggleRow("Hide locked results", options.hideLocked) {
            onAction(SearchDetailAction.ToggleHideLocked)
        }
        ToggleRow("Hide results with no free slots", options.hideNoFreeSlots) {
            onAction(SearchDetailAction.ToggleHideNoFreeSlots)
        }
        ToggleRow("Fold results", options.foldResults) {
            onAction(SearchDetailAction.ToggleFold)
        }

        OutlinedTextField(
            value = options.filterText,
            onValueChange = { onAction(SearchDetailAction.SetFilter(it)) },
            label = { Text("Filter") },
            placeholder = {
                Text(FILTER_PLACEHOLDER, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            },
            singleLine = true,
            trailingIcon = {
                if (options.filterText.isNotEmpty()) {
                    IconButton(onClick = { onAction(SearchDetailAction.ClearFilter) }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear filter",
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
            Text(sort.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ResultSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
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
            contentDescription = if (response.folded) "Expand" else "Collapse",
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        val slot = if (response.hasFreeUploadSlot) "free slot" else "no slot"
        Text(
            "${response.username} · ${response.fileCount} files · $slot · ${response.uploadSpeed / 1024} KB/s · queue ${response.queueLength}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PeerOverflowMenu(response.username, onBrowseUser)
    }
}

/** Per-peer overflow actions. "Message user" (chat DM) will join "Browse user" here later. */
@Composable
private fun PeerOverflowMenu(username: String, onBrowseUser: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More actions for $username")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Browse user") },
                onClick = {
                    expanded = false
                    onBrowseUser(username)
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onAction(SearchDetailAction.ToggleDirectoryCollapse(username, dir.directory)) },
                onLongClick = {
                    onAction(
                        SearchDetailAction.SetDirectorySelection(
                            username = username,
                            files = dir.files.map { it.file },
                            selected = dir.selection != TriState.All,
                        ),
                    )
                },
            )
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (dir.collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = if (dir.collapsed) "Expand directory" else "Collapse directory",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        // No ellipsis: the full remote path scrolls horizontally so long paths stay readable.
        Text(
            dir.directory.ifBlank { "(root)" },
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
                    contentDescription = "Search additional files in this directory",
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
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = shown.selected,
            onCheckedChange = { onAction(SearchDetailAction.ToggleFileSelection(username, file)) },
        )
        if (file.isLocked) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Locked",
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
            Icon(Icons.Filled.Download, contentDescription = "Download")
        }
    }
}

@Composable
private fun Pager(
    remainingCount: Int,
    filteredCount: Int,
    onShowMore: () -> Unit,
) {
    val hidden = "$filteredCount hidden by filters"
    when {
        remainingCount > 0 -> Button(
            onClick = onShowMore,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text("Show ${min(remainingCount, PAGE_SIZE)} more results ($remainingCount remaining, $hidden)")
        }

        filteredCount > 0 -> Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text("All results shown. $hidden")
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

/** size · bitrate · length · type, omitting parts slskd didn't report. */
private fun fileMeta(file: SearchResultFile): String = buildList {
    add(formatBytes(file.sizeBytes))
    file.bitRate?.let { add("$it kbps") }
    file.lengthSeconds?.let { add(formatDuration(it)) }
    file.extension?.takeIf { it.isNotBlank() }?.let { add(it.trimStart('.').uppercase()) }
}.joinToString(" · ")

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
