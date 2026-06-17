package com.slskdandroid.feature.search.impl

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DISPLAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())

/** Formats an slskd ISO-8601 timestamp to a short local string; returns "" if absent/unparseable. */
internal fun formatTimestamp(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching { OffsetDateTime.parse(iso).format(DISPLAY_FORMAT) }
        .recoverCatching {
            // slskd may emit a local DateTime with no offset.
            java.time.LocalDateTime.parse(iso).format(DISPLAY_FORMAT)
        }
        .getOrElse { "" }
}

/** Human-readable byte size, e.g. "4.2 MB". */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
