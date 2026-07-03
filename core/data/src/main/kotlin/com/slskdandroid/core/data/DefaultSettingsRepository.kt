package com.slskdandroid.core.data

import com.slskdandroid.core.datastore.NotificationSettingsDataSource
import com.slskdandroid.core.model.NotificationSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultSettingsRepository @Inject constructor(
    private val dataSource: NotificationSettingsDataSource,
) : SettingsRepository {

    override val notificationSettings: Flow<NotificationSettings> = dataSource.notificationSettings

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataSource.setEnabled(enabled)
    }

    override suspend fun setCheckIntervalSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(
            NotificationSettings.MIN_INTERVAL_SECONDS,
            NotificationSettings.MAX_INTERVAL_SECONDS,
        )
        dataSource.setCheckIntervalSeconds(clamped)
    }
}
