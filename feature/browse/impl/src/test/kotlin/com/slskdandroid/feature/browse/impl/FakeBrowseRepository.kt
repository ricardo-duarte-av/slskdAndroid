package com.slskdandroid.feature.browse.impl

import com.slskdandroid.core.data.BrowseProgress
import com.slskdandroid.core.data.BrowseRepository
import com.slskdandroid.core.data.DownloadsRepository
import com.slskdandroid.core.model.Download
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Test double for [BrowseRepository]; [browseFlow] drives what a browse emits. */
class FakeBrowseRepository : BrowseRepository {
    var browseFlow: (String) -> Flow<BrowseProgress> = { flowOf(BrowseProgress.Loaded(emptyList())) }
    override fun browse(username: String): Flow<BrowseProgress> = browseFlow(username)
}

/** Minimal [DownloadsRepository] double for the browse tests; records enqueue calls. */
class FakeDownloadsRepository : DownloadsRepository {
    val enqueued = mutableListOf<Triple<String, String, Long>>()

    override fun downloads(): Flow<List<Download>> = flowOf(emptyList())
    override suspend fun cancel(username: String, id: String, remove: Boolean) = Unit
    override suspend fun retry(download: Download) = Unit
    override suspend fun enqueue(username: String, filename: String, sizeBytes: Long) {
        enqueued += Triple(username, filename, sizeBytes)
    }
}
