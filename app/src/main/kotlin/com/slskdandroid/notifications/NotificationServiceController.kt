package com.slskdandroid.notifications

import android.content.Context
import androidx.core.content.ContextCompat

/** Thin wrapper over starting/stopping [NotificationService] so callers don't touch Intents. */
object NotificationServiceController {

    fun start(context: Context) {
        ContextCompat.startForegroundService(context, NotificationService.intent(context))
    }

    fun stop(context: Context) {
        context.stopService(NotificationService.intent(context))
    }
}
