package com.slskdandroid.feature.search.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.slskdandroid.core.model.SearchResponse

@Composable
internal fun SearchRoute(
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    SearchScreen(
        query = query,
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    query: String,
    uiState: SearchUiState,
    onAction: (SearchAction) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Search") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { onAction(SearchAction.QueryChanged(it)) },
                label = { Text("What are you looking for?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onAction(SearchAction.Submit) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Search")
            }

            when (uiState) {
                SearchUiState.Idle -> Unit
                SearchUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                is SearchUiState.Error -> Text(uiState.message)
                is SearchUiState.Success -> ResultList(uiState.responses)
            }
        }
    }
}

@Composable
private fun ResultList(responses: List<SearchResponse>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(responses) { response ->
            ListItem(
                headlineContent = { Text(response.username) },
                supportingContent = {
                    Text("${response.files.size} files · queue ${response.queueLength}")
                },
            )
        }
    }
}
