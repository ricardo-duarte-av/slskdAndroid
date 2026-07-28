package com.slskdandroid.feature.search.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.designsystem.component.ReadableWidth
import com.slskdandroid.core.designsystem.component.SettingsActionButton
import com.slskdandroid.core.designsystem.component.asString
import com.slskdandroid.core.model.Search

@Composable
internal fun SearchListRoute(
    onOpenSearch: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: SearchListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.openSearch.collect { id -> onOpenSearch(id) }
    }

    SearchListScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onOpenSearch = onOpenSearch,
        onSettings = onSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchListScreen(
    uiState: SearchListUiState,
    onAction: (SearchListAction) -> Unit,
    onOpenSearch: (String) -> Unit,
    onSettings: () -> Unit,
) {
    // M3 expects a scroll behaviour on app bars over scrolling content; without one the
    // bar is a static block that never yields vertical space.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                actions = { SettingsActionButton(onSettings) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { onAction(SearchListAction.QueryChanged(it)) },
                label = { Text(stringResource(R.string.search_query_label)) },
                singleLine = true,
                trailingIcon = {
                    if (uiState.starting) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                    } else {
                        IconButton(onClick = { onAction(SearchListAction.Submit) }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_action))
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onAction(SearchListAction.Submit) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            when (val state = uiState.searches) {
                SearchesState.Loading -> CenteredMessage(stringResource(R.string.search_loading))

                is SearchesState.Error ->
                    CenteredMessage(state.message.asString(), MaterialTheme.colorScheme.error)

                is SearchesState.Loaded ->
                    if (state.searches.isEmpty()) {
                        CenteredMessage(stringResource(R.string.search_empty))
                    } else {
                        ReadableWidth {
    LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                            ) {
                                items(state.searches, key = { it.id }) { search ->
                                    SearchRow(
                                        search = search,
                                        onOpen = { onOpenSearch(search.id) },
                                        onDelete = { onAction(SearchListAction.Delete(search.id)) },
                                    )
                                    HorizontalDivider()
                                }
                            }
    }
                    }
            }
        }
    }
}

@Composable
private fun SearchRow(
    search: Search,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen, onClickLabel = stringResource(R.string.search_open_results))
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                search.searchText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                statusLabel(search),
                style = MaterialTheme.typography.labelSmall,
                color = if (search.isComplete) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                metaLine(search),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.search_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** Friendly status: the raw slskd flags string while complete, or a live label otherwise. */
@Composable
private fun statusLabel(search: Search): String = when {
    !search.isComplete -> stringResource(R.string.search_status_searching)
    // slskd's own state string passes through untranslated; only our fallback is localized.
    else -> search.state.ifBlank { stringResource(R.string.search_status_completed) }
}

@Composable
private fun metaLine(search: Search): String = buildList {
    add(pluralStringResource(R.plurals.search_meta_files, search.fileCount, search.fileCount))
    if (search.lockedFileCount > 0) {
        add(stringResource(R.string.search_meta_locked, search.lockedFileCount))
    }
    add(
        pluralStringResource(
            R.plurals.search_meta_responses,
            search.responseCount,
            search.responseCount,
        ),
    )
    formatTimestamp(search.endedAt).takeIf { it.isNotEmpty() }?.let(::add)
}.joinToString(" · ")
