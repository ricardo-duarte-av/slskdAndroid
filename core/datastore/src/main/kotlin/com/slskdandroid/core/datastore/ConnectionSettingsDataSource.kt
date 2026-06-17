package com.slskdandroid.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.slskdandroid.core.model.ConnectionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the slskd [ConnectionSettings] in a Preferences DataStore. Emits `null` until
 * the user has configured both a base URL and an API key.
 */
@Singleton
class ConnectionSettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val connectionSettings: Flow<ConnectionSettings?> = dataStore.data.map { prefs ->
        val baseUrl = prefs[KEY_BASE_URL]
        val apiKey = prefs[KEY_API_KEY]
        if (!baseUrl.isNullOrBlank() && !apiKey.isNullOrBlank()) {
            ConnectionSettings(baseUrl = baseUrl, apiKey = apiKey)
        } else {
            null
        }
    }

    suspend fun save(settings: ConnectionSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = settings.baseUrl
            prefs[KEY_API_KEY] = settings.apiKey
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_BASE_URL)
            prefs.remove(KEY_API_KEY)
        }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_API_KEY = stringPreferencesKey("api_key")
    }
}
