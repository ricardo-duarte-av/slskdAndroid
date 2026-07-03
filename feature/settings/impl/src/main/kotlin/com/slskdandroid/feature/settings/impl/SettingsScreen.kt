package com.slskdandroid.feature.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.model.NotificationSettings

@Composable
internal fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(uiState = uiState, onAction = viewModel::onAction, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SettingRow(
                title = "Message notifications",
                subtitle = "Check slskd in the background and notify on new DMs and room mentions.",
            ) {
                Switch(
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = { onAction(SettingsAction.SetNotificationsEnabled(it)) },
                )
            }
            HorizontalDivider()
            if (uiState.notificationsEnabled) {
                IntervalSetting(
                    seconds = uiState.checkIntervalSeconds,
                    onSecondsChange = { onAction(SettingsAction.SetCheckIntervalSeconds(it)) },
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        trailing()
    }
}

@Composable
private fun IntervalSetting(seconds: Int, onSecondsChange: (Int) -> Unit) {
    // Track the slider locally while dragging; commit (persist) only when the drag ends.
    var draft by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Check interval", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatInterval(draft.toInt()), style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onSecondsChange(draft.toInt()) },
            valueRange = SLIDER_MIN..SLIDER_MAX,
            // 30s increments across the range.
            steps = ((SLIDER_MAX - SLIDER_MIN) / STEP_SECONDS).toInt() - 1,
        )
        Text(
            "How often the background service polls for new messages. Shorter intervals are more " +
                "responsive but use more battery and data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatInterval(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60}s"
}

private val SLIDER_MIN = NotificationSettings.MIN_INTERVAL_SECONDS.toFloat()
private const val STEP_SECONDS = 30f
// Cap the slider at 30 minutes for a usable control; the model permits more.
private const val SLIDER_MAX = 1_800f
