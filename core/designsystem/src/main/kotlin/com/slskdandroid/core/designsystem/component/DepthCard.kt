package com.slskdandroid.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Container color for a nested "result" card at [depth]: the same hue at deepening tonality as you
 * descend a peer (0) → directory (1) → file (2) hierarchy, using Material 3's surface tonal ladder
 * (subtly primary-tinted under dynamic color). Shared by Search, Downloads and Uploads so their
 * collapsible peer/folder/file lists read identically. To restyle the whole treatment app-wide —
 * e.g. to an accent tint — change only this function and [nestedCardShape].
 */
@Composable
fun nestedCardColor(depth: Int): Color = when (depth) {
    0 -> MaterialTheme.colorScheme.surfaceContainerLow
    1 -> MaterialTheme.colorScheme.surfaceContainerHigh
    else -> MaterialTheme.colorScheme.surfaceContainerHighest
}

/** Corner radius for a nested card at [depth]; tightens as cards nest deeper, reinforcing containment. */
fun nestedCardShape(depth: Int): Shape = when (depth) {
    0 -> RoundedCornerShape(24.dp)
    1 -> RoundedCornerShape(18.dp)
    else -> RoundedCornerShape(14.dp)
}

/**
 * A nested result card at [depth] (see [nestedCardColor]). Pass [color] to override the tonal
 * default — e.g. a selection highlight on a file row.
 */
@Composable
fun DepthCard(
    depth: Int,
    modifier: Modifier = Modifier,
    color: Color = nestedCardColor(depth),
    content: @Composable () -> Unit,
) {
    Surface(
        color = color,
        shape = nestedCardShape(depth),
        modifier = modifier,
        content = content,
    )
}
