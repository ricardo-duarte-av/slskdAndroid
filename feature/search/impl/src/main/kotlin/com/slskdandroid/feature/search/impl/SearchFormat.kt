package com.slskdandroid.feature.search.impl

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Localized date+time for the search list. Previously `ofPattern("MMM d, HH:mm")`, which forced a
 * US field order and a 24-hour clock on every locale; `ofLocalizedDateTime` follows the user's
 * locale and their 12/24-hour preference.
 */
private val DISPLAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

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
