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

/** size · bitrate · quality · length · type, omitting parts slskd didn't report. */
internal fun fileMeta(file: SearchResultFile): String = buildList {
    add(formatBytes(file.sizeBytes))
    file.bitRate?.let { add("$it kbps") }
    qualityLabel(file)?.let { add(it) }
    file.lengthSeconds?.let { add(formatDuration(it)) }
    file.extension?.takeIf { it.isNotBlank() }?.let { add(it.trimStart('.').uppercase()) }
}.joinToString(" · ")

/**
 * slskd-style audio quality from bit depth + sample rate, e.g. "16/44.1 kHz" (lossless), or just
 * the sample rate when the depth is unknown. Null when neither was reported (typical for lossy).
 */
internal fun qualityLabel(file: SearchResultFile): String? {
    val sampleRate = file.sampleRate?.takeIf { it > 0 }?.let(::formatSampleRate)
    val bitDepth = file.bitDepth?.takeIf { it > 0 }
    return when {
        bitDepth != null && sampleRate != null -> "$bitDepth/$sampleRate"
        else -> sampleRate
    }
}

/** Hz → a compact kHz label: 44100 → "44.1 kHz", 48000 → "48 kHz". */
private fun formatSampleRate(hz: Int): String {
    val khz = hz / 1000.0
    val value = if (khz % 1.0 == 0.0) khz.toInt().toString() else "%.1f".format(khz)
    return "$value kHz"
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
