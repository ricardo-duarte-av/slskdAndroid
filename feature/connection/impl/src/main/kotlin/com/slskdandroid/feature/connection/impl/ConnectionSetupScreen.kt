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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.designsystem.component.SectionHeader

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
        topBar = { TopAppBar(title = { Text("Connect to slskd") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Server")
            Text(
                "Enter the address of your slskd instance and an API key. " +
                    "Both are required — the app authenticates with the API key.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = { onAction(ConnectionSetupAction.BaseUrlChanged(it)) },
                label = { Text("Base URL") },
                placeholder = { Text("http://192.168.1.10:5030") },
                singleLine = true,
                enabled = !uiState.isVerifying,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = { onAction(ConnectionSetupAction.ApiKeyChanged(it)) },
                label = { Text("API key") },
                singleLine = true,
                enabled = !uiState.isVerifying,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { onAction(ConnectionSetupAction.Submit) },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Connecting…")
                } else {
                    Text("Connect")
                }
            }
        }
    }
}
