package com.slskdandroid.feature.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.model.CardTintStyle
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
    // M3 expects a scroll behaviour on app bars over scrolling content; without one the
    // bar is a static block that never yields vertical space.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SettingRow(
                title = stringResource(R.string.settings_notifications_title),
                subtitle = stringResource(R.string.settings_notifications_subtitle),
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
                HorizontalDivider()
            }
            CardStyleSetting(
                style = uiState.cardTintStyle,
                onStyleChange = { onAction(SettingsAction.SetCardTintStyle(it)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardStyleSetting(
    style: CardTintStyle,
    onStyleChange: (CardTintStyle) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            stringResource(R.string.settings_card_style_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.settings_card_style_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        val options = listOf(
            CardTintStyle.Neutral to stringResource(R.string.settings_card_style_neutral),
            CardTintStyle.Accent to stringResource(R.string.settings_card_style_accent),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = style == value,
                    onClick = { onStyleChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}

/**
 * A settings row. Uses M3's [ListItem] rather than a hand-rolled Row+Column+Spacer, so the
 * headline/supporting/trailing slots get their specified metrics, type roles and colours.
 */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    ListItem(
        supportingContent = { Text(subtitle) },
        trailingContent = trailing,
    ) { Text(title) }
}

@Composable
private fun IntervalSetting(seconds: Int, onSecondsChange: (Int) -> Unit) {
    // Track the slider locally while dragging; commit (persist) only when the drag ends.
    var draft by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_interval_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
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
            stringResource(R.string.settings_interval_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Composable: each form is a separate resource so translations can reorder and re-unit it. */
@Composable
private fun formatInterval(seconds: Int): String = when {
    seconds < 60 -> stringResource(R.string.settings_interval_seconds, seconds)
    seconds % 60 == 0 -> stringResource(R.string.settings_interval_minutes, seconds / 60)
    else -> stringResource(R.string.settings_interval_minutes_seconds, seconds / 60, seconds % 60)
}

private val SLIDER_MIN = NotificationSettings.MIN_INTERVAL_SECONDS.toFloat()
private const val STEP_SECONDS = 30f
// Cap the slider at 30 minutes for a usable control; the model permits more.
private const val SLIDER_MAX = 1_800f
