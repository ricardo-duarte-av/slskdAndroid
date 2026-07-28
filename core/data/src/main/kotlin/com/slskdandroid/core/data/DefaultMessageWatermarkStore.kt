package com.slskdandroid.core.data

import com.slskdandroid.core.datastore.MessageWatermarkDataSource
import com.slskdandroid.core.model.MessageWatermarks
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultMessageWatermarkStore @Inject constructor(
    private val dataSource: MessageWatermarkDataSource,
) : MessageWatermarkStore {

    override suspend fun load(): MessageWatermarks = dataSource.load()

    override suspend fun save(watermarks: MessageWatermarks) = dataSource.save(watermarks)
}
