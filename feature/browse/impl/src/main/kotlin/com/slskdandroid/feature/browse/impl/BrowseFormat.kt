package com.slskdandroid.feature.browse.impl

import androidx.compose.runtime.Composable
import com.slskdandroid.core.designsystem.component.formatBitRateLocalized
import com.slskdandroid.core.designsystem.component.formatBytes
import com.slskdandroid.core.designsystem.component.formatDurationLocalized
import com.slskdandroid.core.designsystem.component.qualityLabelLocalized
import com.slskdandroid.core.model.SearchResultFile

/** size · bitrate · quality · length · type, omitting parts slskd didn't report. */
@Composable
internal fun fileMeta(file: SearchResultFile): String = buildList {
    add(formatBytes(file.sizeBytes))
    file.bitRate?.let { add(formatBitRateLocalized(it)) }
    qualityLabelLocalized(file.bitDepth, file.sampleRate)?.let { add(it) }
    file.lengthSeconds?.let { add(formatDurationLocalized(it)) }
    file.extension?.takeIf { it.isNotBlank() }?.let { add(it.trimStart('.').uppercase()) }
}.joinToString(" · ")
