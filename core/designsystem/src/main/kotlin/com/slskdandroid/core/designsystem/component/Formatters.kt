package com.slskdandroid.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.slskdandroid.core.designsystem.R
import java.util.Locale

/**
 * Shared display formatters. These previously existed as four copies of `formatBytes` and two of
 * `qualityLabel`/`formatDuration` across the search, browse, downloads and uploads features, each
 * pinned to `Locale.US`.
 *
 * Two deliberate choices:
 *
 *  - **Sizes stay binary (1024-based).** `android.text.format.Formatter.formatFileSize` would
 *    localize the unit symbols for free, but it is SI (1000-based) on modern Android, so every
 *    size in the app would shift — a 1 GiB file would read "1.07 GB". Soulseek clients report
 *    binary sizes, so matching slskd matters more here than borrowing the platform formatter.
 *  - **`kHz` and `kbps` are not translated.** They are SI/technical symbols, written the same way
 *    across locales. Byte units *are* resource-backed, because some locales do transliterate them.
 *
 * What was actually broken was the **number**, not the symbol: `Locale.US` forced a `.` decimal
 * separator regardless of the user's locale.
 */

/**
 * Human-readable byte size, e.g. "4.2 MB" — binary units, decimal separator per the user's locale.
 *
 * The locale comes from [LocalConfiguration] rather than `Locale.getDefault()`: the latter is not
 * observable from composition, so a runtime locale change would leave already-composed sizes
 * formatted for the old locale until something else happened to recompose them.
 */
@Composable
fun formatBytes(bytes: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    if (bytes < BYTES_PER_UNIT) return stringResource(R.string.ds_size_bytes, bytes)
    val units = listOf(R.string.ds_size_kb, R.string.ds_size_mb, R.string.ds_size_gb, R.string.ds_size_tb)
    var value = bytes / BYTES_PER_UNIT
    var unitIndex = 0
    while (value >= BYTES_PER_UNIT && unitIndex < units.lastIndex) {
        value /= BYTES_PER_UNIT
        unitIndex++
    }
    return stringResource(units[unitIndex], String.format(locale, "%.1f", value))
}

/**
 * Composition-aware companions to the pure formatters below, for call sites inside composables.
 * They read the locale observably, so a runtime locale change reformats them.
 */
@Composable
fun formatDurationLocalized(seconds: Int): String =
    formatDuration(seconds, LocalConfiguration.current.locales[0])

@Composable
fun formatBitRateLocalized(kbps: Int): String =
    formatBitRate(kbps, LocalConfiguration.current.locales[0])

@Composable
fun qualityLabelLocalized(bitDepth: Int?, sampleRate: Int?): String? =
    qualityLabel(bitDepth, sampleRate, LocalConfiguration.current.locales[0])

/**
 * slskd-style audio quality from bit depth + sample rate, e.g. "16/44.1 kHz" (lossless), or just
 * the sample rate when the depth is unknown. Null when neither was reported (typical for lossy).
 *
 * Pure rather than `@Composable` so it stays a plain JVM unit test; [locale] is explicit so those
 * tests are deterministic regardless of the machine's default.
 */
fun qualityLabel(
    bitDepth: Int?,
    sampleRate: Int?,
    locale: Locale = Locale.getDefault(),
): String? {
    val rate = sampleRate?.takeIf { it > 0 }?.let { formatSampleRate(it, locale) }
    val depth = bitDepth?.takeIf { it > 0 }
    return when {
        depth != null && rate != null -> "$depth/$rate"
        else -> rate
    }
}

/** Hz → a compact kHz label: 44100 → "44.1 kHz", 48000 → "48 kHz". */
private fun formatSampleRate(hz: Int, locale: Locale): String {
    val khz = hz / 1000.0
    val value = if (khz % 1.0 == 0.0) {
        khz.toInt().toString()
    } else {
        String.format(locale, "%.1f", khz)
    }
    return "$value kHz"
}

/** Track length as m:ss. */
fun formatDuration(seconds: Int, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%d:%02d", seconds / 60, seconds % 60)

/** Bit rate as slskd reports it, e.g. "320 kbps". */
fun formatBitRate(kbps: Int, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%d kbps", kbps)

private const val BYTES_PER_UNIT = 1024.0
