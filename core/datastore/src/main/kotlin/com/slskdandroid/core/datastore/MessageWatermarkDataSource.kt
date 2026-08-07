package com.slskdandroid.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.slskdandroid.core.model.MessageWatermarks
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists [MessageWatermarks] — the "newest message already seen" state the background poll uses
 * to avoid re-notifying. Per-conversation entries are stored as prefixed keys ([DM_PREFIX] /
 * [ROOM_PREFIX]) in the same Preferences DataStore as the rest of the settings.
 */
@Singleton
class MessageWatermarkDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun load(): MessageWatermarks {
        val prefs = dataStore.data.first()
        val dms = mutableMapOf<String, Long>()
        val rooms = mutableMapOf<String, Long>()
        prefs.asMap().forEach { (key, value) ->
            val millis = value as? Long ?: return@forEach
            when {
                key.name.startsWith(DM_PREFIX) -> dms[key.name.removePrefix(DM_PREFIX)] = millis
                key.name.startsWith(ROOM_PREFIX) -> rooms[key.name.removePrefix(ROOM_PREFIX)] = millis
            }
        }
        return MessageWatermarks(
            baselined = prefs[KEY_BASELINED] ?: false,
            baselineFloorMs = prefs[KEY_BASELINE_FLOOR] ?: 0L,
            directMessages = dms,
            rooms = rooms,
        )
    }

    suspend fun save(watermarks: MessageWatermarks) {
        dataStore.edit { prefs ->
            prefs[KEY_BASELINED] = watermarks.baselined
            prefs[KEY_BASELINE_FLOOR] = watermarks.baselineFloorMs
            // Rewrite wholesale: drop every existing per-conversation key first so peers/rooms
            // that disappeared server-side don't linger forever.
            val stale = prefs.asMap().keys
                .filter { it.name.startsWith(DM_PREFIX) || it.name.startsWith(ROOM_PREFIX) }
                .toList()
            stale.forEach { prefs.remove(it) }
            watermarks.directMessages.forEach { (peer, ms) -> prefs[longPreferencesKey(DM_PREFIX + peer)] = ms }
            watermarks.rooms.forEach { (room, ms) -> prefs[longPreferencesKey(ROOM_PREFIX + room)] = ms }
        }
    }

    private companion object {
        val KEY_BASELINED = booleanPreferencesKey("messages_baselined")
        val KEY_BASELINE_FLOOR = longPreferencesKey("messages_baseline_floor_ms")
        const val DM_PREFIX = "wm_dm_"
        const val ROOM_PREFIX = "wm_room_"
    }
}
