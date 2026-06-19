package com.slskdandroid.feature.downloads.impl

import com.slskdandroid.core.data.DownloadsRepository
import com.slskdandroid.core.model.Download
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Test double for [DownloadsRepository]; records cancel/retry/enqueue calls. */
class FakeDownloadsRepository : DownloadsRepository {

    var downloadsFlow: Flow<List<Download>> = flowOf(emptyList())

    val cancelled = mutableListOf<Triple<String, String, Boolean>>()
    val retried = mutableListOf<Download>()
    val enqueued = mutableListOf<Triple<String, String, Long>>()

    override fun downloads(): Flow<List<Download>> = downloadsFlow

    override suspend fun cancel(username: String, id: String, remove: Boolean) {
        cancelled += Triple(username, id, remove)
    }

    override suspend fun retry(download: Download) {
        retried += download
    }

    override suspend fun enqueue(username: String, filename: String, sizeBytes: Long) {
        enqueued += Triple(username, filename, sizeBytes)
    }
}
