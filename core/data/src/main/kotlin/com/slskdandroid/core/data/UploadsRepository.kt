package com.slskdandroid.core.data

import com.slskdandroid.core.model.Upload
import kotlinx.coroutines.flow.Flow

/**
 * Exposes the user's uploads from the slskd backend. As with downloads, slskd pushes no transfer
 * state over a hub, so [uploads] polls the REST endpoint and emits the flattened list on an
 * interval. Uploads are peer-driven, so there is no retry/re-send — only cancel and remove.
 */
interface UploadsRepository {

    /** A live, polling stream of all uploads (in-progress, queued, completed, failed). */
    fun uploads(): Flow<List<Upload>>

    /**
     * Cancels the upload [id] to [username]. Pass [remove] = true to also drop it from the server's
     * list (clearing a finished or failed entry); false merely stops an active transfer.
     */
    suspend fun cancel(username: String, id: String, remove: Boolean)
}
