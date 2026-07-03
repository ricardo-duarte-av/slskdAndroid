package com.slskdandroid.core.model

/**
 * User preferences for background message notifications. When [enabled] is false the polling
 * foreground service is not run at all. [checkIntervalSeconds] is how often the service polls slskd
 * for new DMs and room mentions; it is only meaningful while [enabled] is true.
 */
data class NotificationSettings(
    val enabled: Boolean = DEFAULT_ENABLED,
    val checkIntervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
) {
    companion object {
        const val DEFAULT_ENABLED = false
        const val DEFAULT_INTERVAL_SECONDS = 300

        /** Guard rails for the interval the UI lets the user pick. */
        const val MIN_INTERVAL_SECONDS = 30
        const val MAX_INTERVAL_SECONDS = 3_600
    }
}
