package com.slskdandroid.core.model

/**
 * User preferences for background message notifications. When [enabled] is false the polling work
 * is not scheduled at all. [checkIntervalSeconds] is how often the periodic worker polls slskd for
 * new DMs and room mentions; it is only meaningful while [enabled] is true.
 *
 * The floor is 15 minutes because the poll runs as WorkManager **periodic** work, whose minimum
 * period the platform caps at 15 minutes — anything shorter is silently raised, so the UI must not
 * pretend otherwise.
 */
data class NotificationSettings(
    val enabled: Boolean = DEFAULT_ENABLED,
    val checkIntervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
) {
    companion object {
        const val DEFAULT_ENABLED = false
        const val DEFAULT_INTERVAL_SECONDS = 900

        /** Guard rails for the interval the UI lets the user pick. */
        const val MIN_INTERVAL_SECONDS = 900
        const val MAX_INTERVAL_SECONDS = 3_600
    }
}
