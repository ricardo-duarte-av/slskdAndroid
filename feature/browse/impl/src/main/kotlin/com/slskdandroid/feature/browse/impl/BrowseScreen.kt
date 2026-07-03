package com.slskdandroid.feature.browse.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.designsystem.component.SettingsActionButton

@Composable
internal fun BrowseRoute(
    onSettings: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BrowseScreen(uiState = uiState, onAction = viewModel::onAction, onSettings = onSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseScreen(
    uiState: BrowseUiState,
    onAction: (BrowseAction) -> Unit,
    onSettings: () -> Unit,
) {
    val phase = uiState.phase

    // Back: from a file list return to the tree; while browsing a user, close that user.
    BackHandler(enabled = phase is BrowsePhase.Files) { onAction(BrowseAction.CloseDirectory) }
    BackHandler(enabled = phase is BrowsePhase.Tree || phase is BrowsePhase.Loading || phase is BrowsePhase.Error) {
        onAction(BrowseAction.CloseUser)
    }

    Scaffold(
        topBar = { BrowseTopBar(phase, onAction, onSettings) },
        bottomBar = {
            if (phase is BrowsePhase.Files && uiState.selectedCount > 0) {
                SelectionBar(
                    count = uiState.selectedCount,
                    sizeBytes = uiState.selectedSizeBytes,
                    onClear = { onAction(BrowseAction.ClearSelection) },
                    onDownload = { onAction(BrowseAction.DownloadSelected) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (phase) {
                BrowsePhase.Idle -> IdlePrompt(uiState.query, onAction)

                is BrowsePhase.Loading -> CenteredContent {
                    val percent = phase.percent
                    if (percent == null) {
                        CircularProgressIndicator()
                        Text("Browsing ${phase.username}…", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        CircularProgressIndicator(progress = { percent / 100f })
                        Text("Browsing ${phase.username}… $percent%", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                is BrowsePhase.Error -> CenteredMessage(phase.message, MaterialTheme.colorScheme.error)

                is BrowsePhase.Tree -> Column(Modifier.fillMaxSize()) {
                    FilterField(phase.filter, "Filter folders") { onAction(BrowseAction.SetTreeFilter(it)) }
                    if (phase.rows.isEmpty()) {
                        CenteredMessage(
                            if (phase.filter.isBlank()) {
                                "${phase.username} isn't sharing any files."
                            } else {
                                "No folders match “${phase.filter}”."
                            },
                        )
                    } else {
                        DirectoryTree(phase.rows, onAction)
                    }
                }

                is BrowsePhase.Files -> Column(Modifier.fillMaxSize()) {
                    FilterField(phase.filter, "Filter files") { onAction(BrowseAction.SetFileFilter(it)) }
                    FileList(phase, onAction)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseTopBar(phase: BrowsePhase, onAction: (BrowseAction) -> Unit, onSettings: () -> Unit) {
    val closeAction: @Composable () -> Unit = {
        IconButton(onClick = { onAction(BrowseAction.CloseUser) }) {
            Icon(Icons.Filled.Close, contentDescription = "Close user")
        }
    }
    when (phase) {
        BrowsePhase.Idle -> TopAppBar(title = { Text("Browse") }, actions = { SettingsActionButton(onSettings) })

        is BrowsePhase.Loading -> TopAppBar(title = { Text(phase.username) }, actions = { closeAction() })
        is BrowsePhase.Error -> TopAppBar(title = { Text(phase.username) }, actions = { closeAction() })
        is BrowsePhase.Tree -> TopAppBar(title = { Text(phase.username) }, actions = { closeAction() })

        is BrowsePhase.Files -> TopAppBar(
            navigationIcon = {
                IconButton(onClick = { onAction(BrowseAction.CloseDirectory) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to folders")
                }
            },
            title = {
                Text(baseName(phase.directory), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            actions = { closeAction() },
        )
    }
}

@Composable
private fun IdlePrompt(query: String, onAction: (BrowseAction) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = { onAction(BrowseAction.QueryChanged(it)) },
        label = { Text("Username") },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { onAction(BrowseAction.Submit) }) {
                Icon(Icons.Filled.Folder, contentDescription = "Browse")
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onAction(BrowseAction.Submit) }),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
    CenteredMessage("Enter a username to browse their shared files.")
}

@Composable
private fun FilterField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear filter")
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun DirectoryTree(rows: List<TreeRow>, onAction: (BrowseAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(rows, key = { it.path }) { row ->
            TreeNodeRow(row, onAction, Modifier.animateItem())
        }
    }
}

@Composable
private fun TreeNodeRow(
    row: TreeRow,
    onAction: (BrowseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                // Tapping a folder with files opens it; otherwise it expands/collapses.
                val exactPath = row.exactPath
                if (exactPath != null && row.fileCount > 0) {
                    onAction(BrowseAction.OpenDirectory(exactPath))
                } else if (row.hasChildren) {
                    onAction(BrowseAction.ToggleExpand(row.path))
                }
            }
            .padding(
                start = (12 + row.depth * 16).dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.hasChildren) {
            IconButton(
                onClick = { onAction(BrowseAction.ToggleExpand(row.path)) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (row.expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (row.expanded) "Collapse" else "Expand",
                )
            }
        } else {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            row.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (row.fileCount > 0) {
            Text(
                "${row.fileCount} files",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FileList(phase: BrowsePhase.Files, onAction: (BrowseAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "path-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TriStateCheckbox(
                    state = when (phase.selection) {
                        TriState.None -> ToggleableState.Off
                        TriState.Some -> ToggleableState.Indeterminate
                        TriState.All -> ToggleableState.On
                    },
                    onClick = {
                        onAction(
                            BrowseAction.SetAllSelection(
                                files = phase.files.map { it.file },
                                selected = phase.selection != TriState.All,
                            ),
                        )
                    },
                )
                Text(
                    phase.directory,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                )
            }
            HorizontalDivider()
        }

        if (phase.files.isEmpty()) {
            item(key = "no-match") {
                Text(
                    "No files match “${phase.filter}”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }

        items(phase.files, key = { it.file.filename }) { shown ->
            FileRow(shown, onAction)
        }
    }
}

@Composable
private fun FileRow(shown: ShownFile, onAction: (BrowseAction) -> Unit) {
    val file = shown.file
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = shown.selected,
            onCheckedChange = { onAction(BrowseAction.ToggleFileSelection(file)) },
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
            onClick = { onAction(BrowseAction.Download(file)) },
            enabled = !file.isLocked,
        ) {
            Icon(Icons.Filled.Download, contentDescription = "Download")
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
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

private fun baseName(path: String): String =
    path.trimEnd('\\', '/').substringAfterLast('\\').substringAfterLast('/')
