package com.slskdandroid.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.slskdandroid.core.model.NotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the background-notification [NotificationSettings] in the same Preferences DataStore
 * used for the connection. Emits defaults (disabled, 15 min) until the user changes them.
 */
@Singleton
class NotificationSettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val notificationSettings: Flow<NotificationSettings> = dataStore.data.map { prefs ->
        NotificationSettings(
            enabled = prefs[KEY_ENABLED] ?: NotificationSettings.DEFAULT_ENABLED,
            // Clamped on read as well as on write: intervals persisted by older versions (which
            // allowed as little as 30s, back when the poll ran in a foreground service) are below
            // WorkManager's 15-minute periodic floor.
            checkIntervalSeconds = (prefs[KEY_INTERVAL] ?: NotificationSettings.DEFAULT_INTERVAL_SECONDS)
                .coerceIn(NotificationSettings.MIN_INTERVAL_SECONDS, NotificationSettings.MAX_INTERVAL_SECONDS),
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ENABLED] = enabled }
    }

    suspend fun setCheckIntervalSeconds(seconds: Int) {
        dataStore.edit { prefs -> prefs[KEY_INTERVAL] = seconds }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_INTERVAL = intPreferencesKey("notifications_interval_seconds")
    }
}
