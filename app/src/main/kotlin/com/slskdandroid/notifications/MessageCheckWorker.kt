package com.slskdandroid.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.slskdandroid.core.data.MessageNotifier
import com.slskdandroid.core.data.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * One background poll for new DMs and room mentions, run as WorkManager periodic work (scheduled by
 * [NotificationWorkScheduler]). slskd pushes nothing, so this pull loop is the only way to surface
 * messages while the app is closed.
 *
 * Dependencies come from Hilt via [EntryPointAccessors] rather than `@HiltWorker`: that keeps the
 * androidx.hilt worker processor — an extra KSP step on an already bleeding-edge toolchain — out of
 * the build, and this worker needs exactly two singletons.
 */
class MessageCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun messageNotifier(): MessageNotifier
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)

        // Defensive: the scheduler cancels this work when notifications are switched off, but a
        // run already queued at that moment would still fire.
        if (!deps.settingsRepository().notificationSettings.first().enabled) {
            Log.d(TAG, "notifications disabled — cancelling scheduled work")
            NotificationWorkScheduler.cancel(applicationContext)
            return Result.success()
        }

        Log.d(TAG, "polling slskd for new messages")
        // A failed scan (server down, no route to host) isn't worth a retry with backoff — the next
        // period comes around soon enough — so report success either way and let the log tell.
        runCatching { deps.messageNotifier().scanOnce() }
            .onFailure { Log.w(TAG, "scan failed", it) }
        return Result.success()
    }

    private companion object {
        const val TAG = "MessageCheckWorker"
    }
}
