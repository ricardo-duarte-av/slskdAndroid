package com.slskdandroid.core.data

import com.slskdandroid.core.model.MessageWatermarks

/**
 * Load/store for the background notifier's de-duplication state. An interface so [MessageNotifier]
 * can be exercised against an in-memory fake, and so the DataStore type stays off its public
 * constructor.
 */
interface MessageWatermarkStore {
    suspend fun load(): MessageWatermarks
    suspend fun save(watermarks: MessageWatermarks)
}
