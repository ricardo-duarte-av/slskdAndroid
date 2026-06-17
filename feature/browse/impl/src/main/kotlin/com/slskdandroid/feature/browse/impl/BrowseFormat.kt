package com.slskdandroid.feature.browse.impl

import com.slskdandroid.core.model.SearchResultFile
import java.util.Locale

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

/** size · bitrate · length · type, omitting parts slskd didn't report. */
internal fun fileMeta(file: SearchResultFile): String = buildList {
    add(formatBytes(file.sizeBytes))
    file.bitRate?.let { add("$it kbps") }
    file.lengthSeconds?.let { add(formatDuration(it)) }
    file.extension?.takeIf { it.isNotBlank() }?.let { add(it.trimStart('.').uppercase()) }
}.joinToString(" · ")

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
