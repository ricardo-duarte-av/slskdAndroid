package com.slskdandroid.core.model

/**
 * De-duplication state for background message notifications: the newest server timestamp already
 * seen per DM peer and per room, plus whether the initial silent baseline has been taken.
 *
 * This is persisted rather than kept in memory because the poll runs as a WorkManager worker — each
 * run may happen in a fresh process, so an in-memory watermark would re-baseline every time and
 * never notify anything.
 */
data class MessageWatermarks(
    val baselined: Boolean = false,
    val baselineFloorMs: Long = 0L,
    val directMessages: Map<String, Long> = emptyMap(),
    val rooms: Map<String, Long> = emptyMap(),
)
