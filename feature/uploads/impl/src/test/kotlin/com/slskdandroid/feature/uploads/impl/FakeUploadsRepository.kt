package com.slskdandroid.feature.uploads.impl

import com.slskdandroid.core.data.UploadsRepository
import com.slskdandroid.core.model.Upload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Test double for [UploadsRepository]; records cancel calls. */
class FakeUploadsRepository : UploadsRepository {

    var uploadsFlow: Flow<List<Upload>> = flowOf(emptyList())

    val cancelled = mutableListOf<Triple<String, String, Boolean>>()

    override fun uploads(): Flow<List<Upload>> = uploadsFlow

    override suspend fun cancel(username: String, id: String, remove: Boolean) {
        cancelled += Triple(username, id, remove)
    }
}
