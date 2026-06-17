package com.slskdandroid.core.data

import com.slskdandroid.core.model.Download
import kotlinx.coroutines.flow.Flow

/**
 * Exposes the user's downloads from the slskd backend. slskd does not push transfer state over a
 * SignalR hub (unlike searches), so [downloads] polls the REST endpoint and emits the flattened,
 * up-to-date list on an interval until the collection is cancelled.
 */
interface DownloadsRepository {

    /** A live, polling stream of all downloads (in-progress, queued, completed, failed). */
    fun downloads(): Flow<List<Download>>

    /**
     * Cancels the download [id] from [username]. Pass [remove] = true to also drop it from the
     * server's list (clearing a finished or failed entry); false merely stops an active transfer.
     */
    suspend fun cancel(username: String, id: String, remove: Boolean)

    /** Re-enqueues [download] (by its filename + size) to retry a failed or cancelled transfer. */
    suspend fun retry(download: Download)

    /** Enqueues a download of [filename] (of [sizeBytes]) from [username]; used from search results. */
    suspend fun enqueue(username: String, filename: String, sizeBytes: Long)
}
