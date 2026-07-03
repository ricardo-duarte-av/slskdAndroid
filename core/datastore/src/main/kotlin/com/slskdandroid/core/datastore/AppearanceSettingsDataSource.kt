package com.slskdandroid.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.slskdandroid.core.model.CardTintStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists appearance preferences (currently the card tint style) in the same Preferences DataStore
 * used for the connection. Emits the default until the user changes it; an unknown stored value
 * falls back to the default rather than crashing.
 */
@Singleton
class AppearanceSettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val cardTintStyle: Flow<CardTintStyle> = dataStore.data.map { prefs ->
        prefs[KEY_CARD_TINT]
            ?.let { name -> runCatching { CardTintStyle.valueOf(name) }.getOrNull() }
            ?: CardTintStyle.Default
    }

    suspend fun setCardTintStyle(style: CardTintStyle) {
        dataStore.edit { prefs -> prefs[KEY_CARD_TINT] = style.name }
    }

    private companion object {
        val KEY_CARD_TINT = stringPreferencesKey("card_tint_style")
    }
}
