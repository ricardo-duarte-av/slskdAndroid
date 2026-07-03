package com.slskdandroid.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Coarse transfer lifecycle, shared by Downloads and Uploads (which have parallel state enums). */
enum class TransferPhase { Queued, InProgress, Completed, Failed, Unknown }

/** One transfer's contribution to an aggregate: its [phase] and live [percentComplete] (0..100). */
data class TransferItem(val phase: TransferPhase, val percentComplete: Double)

/** Tallied state of a group of transfers, plus the aggregate [progress] (0f..1f). */
data class TransferStatus(
    val total: Int,
    val queued: Int,
    val inProgress: Int,
    val completed: Int,
    val failed: Int,
    val unknown: Int,
    /**
     * Mean per-item completion: completed counts 1.0, in-progress its live fraction, queued/errored
     * 0.0. So a parent bar tracks real progress — it never reads full while items are still running.
     */
    val progress: Float,
) {
    /** True when at least one item is actively transferring (→ show the progress bar). */
    val isActive: Boolean get() = inProgress > 0
}

/**
 * Aggregates [items] into a [TransferStatus]. The [TransferStatus.progress] is the mean of each
 * item's completion (see [TransferStatus.progress]) — the "average of all items" a parent card shows.
 */
fun transferStatusOf(items: List<TransferItem>): TransferStatus {
    if (items.isEmpty()) return TransferStatus(0, 0, 0, 0, 0, 0, 0f)
    var queued = 0
    var inProgress = 0
    var completed = 0
    var failed = 0
    var unknown = 0
    var completionSum = 0.0
    for (item in items) {
        when (item.phase) {
            TransferPhase.Queued -> queued++
            TransferPhase.InProgress -> {
                inProgress++
                completionSum += (item.percentComplete / 100.0).coerceIn(0.0, 1.0)
            }
            TransferPhase.Completed -> {
                completed++
                completionSum += 1.0
            }
            TransferPhase.Failed -> failed++
            TransferPhase.Unknown -> unknown++
        }
    }
    return TransferStatus(
        total = items.size,
        queued = queued,
        inProgress = inProgress,
        completed = completed,
        failed = failed,
        unknown = unknown,
        progress = (completionSum / items.size).toFloat(),
    )
}

/** Compact "N complete · M errored · …" over the non-zero terminal buckets. */
private fun TransferStatus.mixedSummary(): String = buildList {
    if (completed > 0) add("$completed complete")
    if (queued > 0) add("$queued queued")
    if (failed > 0) add("$failed errored")
    if (unknown > 0) add("$unknown unknown")
}.joinToString(" · ")

/**
 * A one-line transfer indicator for a card. If anything is in progress it shows the Material 3
 * expressive wavy progress bar (aggregate across the group); otherwise a status label — "Complete",
 * "Queued" or "Errored" when the whole group shares one state, or a short count summary when mixed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransferStatusLine(status: TransferStatus, modifier: Modifier = Modifier) {
    if (status.total == 0) return

    if (status.isActive) {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LinearWavyProgressIndicator(
                progress = { status.progress },
                modifier = Modifier.weight(1f).height(8.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${(status.progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val scheme = MaterialTheme.colorScheme
    val (label, color) = when {
        status.completed == status.total -> "Complete" to scheme.primary
        status.queued == status.total -> "Queued" to scheme.tertiary
        status.failed == status.total -> "Errored" to scheme.error
        status.unknown == status.total -> "Unknown" to scheme.onSurfaceVariant
        else -> status.mixedSummary() to scheme.onSurfaceVariant
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}
