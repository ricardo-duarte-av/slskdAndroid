package com.slskdandroid.core.data

import com.slskdandroid.core.model.ConnectionSettings
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the slskd connection. Exposes the persisted settings and
 * validates candidate settings against the server before persisting them.
 */
interface ConnectionSettingsRepository {

    /** Persisted settings, or `null` until the user has configured a connection. */
    val connectionSettings: Flow<ConnectionSettings?>

    /**
     * Verifies [settings] against the slskd server and, only on success, persists them and
     * activates them for the network layer.
     */
    suspend fun verifyAndSave(settings: ConnectionSettings): Result<Unit>

    /** Clears the stored connection, returning the app to its unconfigured state. */
    suspend fun clear()
}
