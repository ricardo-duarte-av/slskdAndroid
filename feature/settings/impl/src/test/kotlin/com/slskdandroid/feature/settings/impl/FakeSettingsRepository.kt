package com.slskdandroid.feature.settings.impl

import com.slskdandroid.core.data.SettingsRepository
import com.slskdandroid.core.model.NotificationSettings
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SettingsRepository] fake that mirrors the clamping the real one performs. */
class FakeSettingsRepository : SettingsRepository {
    val state = MutableStateFlow(NotificationSettings())
    override val notificationSettings = state

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        state.value = state.value.copy(enabled = enabled)
    }

    override suspend fun setCheckIntervalSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(
            NotificationSettings.MIN_INTERVAL_SECONDS,
            NotificationSettings.MAX_INTERVAL_SECONDS,
        )
        state.value = state.value.copy(checkIntervalSeconds = clamped)
    }
}
