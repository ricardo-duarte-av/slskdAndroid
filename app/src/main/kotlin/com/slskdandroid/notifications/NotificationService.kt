package com.slskdandroid.notifications

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.slskdandroid.core.data.MessageNotifier
import com.slskdandroid.core.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A permanent foreground service that polls slskd for new DMs and room mentions on the user's
 * chosen interval and posts message notifications (via [MessageNotifier]). slskd pushes nothing, so
 * this pull loop is the only way to surface messages while the app is backgrounded.
 *
 * Started/stopped by [NotificationServiceController] as the connection and the notifications
 * setting change. The loop also self-stops if it observes the setting turned off.
 */
@AndroidEntryPoint
class NotificationService : Service() {

    @Inject lateinit var notifier: MessageNotifier
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (loopJob?.isActive != true) {
            loopJob = scope.launch {
                while (isActive) {
                    val settings = settingsRepository.notificationSettings.first()
                    if (!settings.enabled) {
                        android.util.Log.d(TAG, "notifications disabled — stopping service")
                        stopSelf()
                        break
                    }
                    android.util.Log.d(TAG, "polling (interval=${settings.checkIntervalSeconds}s)")
                    runCatching { notifier.scanOnce() }
                        .onFailure { android.util.Log.w(TAG, "scan failed", it) }
                    delay(settings.checkIntervalSeconds.toLong() * 1_000L)
                }
            }
        }
        // Restart if the system kills us; the controller re-issues start on next app launch anyway.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("slskd")
            .setContentText("Watching for new messages")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                packageManager.getLaunchIntentForPackage(packageName)?.let {
                    android.app.PendingIntent.getActivity(
                        this,
                        0,
                        it,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    )
                },
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Message watch")
            .setDescription("Keeps checking slskd for new messages")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "message_watch"
        private const val FOREGROUND_ID = 1

        fun intent(context: Context): Intent = Intent(context, NotificationService::class.java)
    }
}
