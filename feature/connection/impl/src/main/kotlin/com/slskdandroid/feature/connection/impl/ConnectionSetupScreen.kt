package com.slskdandroid.feature.connection.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.designsystem.component.SectionHeader
import com.slskdandroid.core.designsystem.component.asString

@Composable
internal fun ConnectionSetupRoute(
    onConnectionEstablished: () -> Unit,
    viewModel: ConnectionSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.connectionEstablished.collect { onConnectionEstablished() }
    }

    ConnectionSetupScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionSetupScreen(
    uiState: ConnectionSetupUiState,
    onAction: (ConnectionSetupAction) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.connection_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.connection_section_server))
            Text(
                stringResource(R.string.connection_intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = { onAction(ConnectionSetupAction.BaseUrlChanged(it)) },
                label = { Text(stringResource(R.string.connection_base_url_label)) },
                placeholder = { Text(stringResource(R.string.connection_base_url_placeholder)) },
                singleLine = true,
                enabled = !uiState.isVerifying,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = { onAction(ConnectionSetupAction.ApiKeyChanged(it)) },
                label = { Text(stringResource(R.string.connection_api_key_label)) },
                singleLine = true,
                enabled = !uiState.isVerifying,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.errorMessage?.let { message ->
                Text(message.asString(), color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { onAction(ConnectionSetupAction.Submit) },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.connection_connecting))
                } else {
                    Text(stringResource(R.string.connection_connect))
                }
            }
        }
    }
}
