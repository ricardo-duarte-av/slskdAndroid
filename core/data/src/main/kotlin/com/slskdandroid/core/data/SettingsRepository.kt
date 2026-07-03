package com.slskdandroid.core.data

import com.slskdandroid.core.model.CardTintStyle
import com.slskdandroid.core.model.NotificationSettings
import kotlinx.coroutines.flow.Flow

/** Reads and writes the user's app preferences (background notifications + appearance). */
interface SettingsRepository {

    /** A live stream of the current notification settings (emits defaults until changed). */
    val notificationSettings: Flow<NotificationSettings>

    suspend fun setNotificationsEnabled(enabled: Boolean)

    /** Persists the poll interval, clamped to [NotificationSettings]'s guard rails. */
    suspend fun setCheckIntervalSeconds(seconds: Int)

    /** A live stream of the card tint style (emits [CardTintStyle.Default] until changed). */
    val cardTintStyle: Flow<CardTintStyle>

    suspend fun setCardTintStyle(style: CardTintStyle)
}
