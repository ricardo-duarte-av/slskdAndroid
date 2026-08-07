package com.slskdandroid.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules/cancels the periodic [MessageCheckWorker]. Replaces the old polling foreground service:
 * WorkManager survives reboots and process death on its own and needs no `FOREGROUND_SERVICE*`
 * permission, at the cost of a 15-minute minimum period and inexact firing (the system batches work
 * and defers it in Doze).
 */
object NotificationWorkScheduler {

    private const val WORK_NAME = "slskd-message-check"

    fun schedule(context: Context, intervalSeconds: Int) {
        val request = PeriodicWorkRequestBuilder<MessageCheckWorker>(
            workIntervalMinutes(intervalSeconds),
            TimeUnit.MINUTES,
        ).setConstraints(
            // Pointless to wake up and hit the API with no connectivity.
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).build()

        // UPDATE (not REPLACE): changing the interval re-specs the existing work instead of
        // restarting its schedule, so toggling settings doesn't reset the next run.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * The user's interval in whole minutes, never below WorkManager's 15-minute periodic floor —
     * shorter periods are silently raised by the platform anyway.
     */
    internal fun workIntervalMinutes(intervalSeconds: Int): Long =
        (intervalSeconds / 60L).coerceAtLeast(MIN_PERIODIC_MINUTES)

    private const val MIN_PERIODIC_MINUTES = 15L
}
