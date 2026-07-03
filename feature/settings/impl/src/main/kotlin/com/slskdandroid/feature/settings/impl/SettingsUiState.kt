package com.slskdandroid.feature.settings.impl

import com.slskdandroid.core.model.NotificationSettings

/** UI state for the Settings screen — a direct projection of the persisted notification settings. */
data class SettingsUiState(
    val notificationsEnabled: Boolean = NotificationSettings.DEFAULT_ENABLED,
    val checkIntervalSeconds: Int = NotificationSettings.DEFAULT_INTERVAL_SECONDS,
)

/** User intents from the Settings screen. */
sealed interface SettingsAction {
    data class SetNotificationsEnabled(val enabled: Boolean) : SettingsAction
    data class SetCheckIntervalSeconds(val seconds: Int) : SettingsAction
}
