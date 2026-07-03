package com.slskdandroid.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * Whether the nested cards use the accent tint (true) or the neutral surface ladder (false).
 * Provided by `SlskdTheme` from the user's Settings → Card style choice, so all three screens
 * (Search, Downloads, Uploads) switch together. Defaults to neutral.
 */
val LocalUseAccentCards = staticCompositionLocalOf { false }

/**
 * Container color for a nested "result" card at [depth]: the same hue at deepening tonality as you
 * descend a peer (0) → directory (1) → file (2) hierarchy. Shared by Search, Downloads and Uploads
 * so their collapsible peer/folder/file lists read identically. Follows [LocalUseAccentCards]:
 * either Material 3's neutral surface tonal ladder or a primary-accent tint that deepens with depth.
 */
@Composable
fun nestedCardColor(depth: Int): Color =
    if (LocalUseAccentCards.current) accentCardColor(depth) else neutralCardColor(depth)

/** Neutral surface tonal ladder (subtly primary-tinted under dynamic color). */
@Composable
private fun neutralCardColor(depth: Int): Color = when (depth) {
    0 -> MaterialTheme.colorScheme.surfaceContainerLow
    1 -> MaterialTheme.colorScheme.surfaceContainerHigh
    else -> MaterialTheme.colorScheme.surfaceContainerHighest
}

/** Primary blended over the surface at increasing strength with depth. */
@Composable
private fun accentCardColor(depth: Int): Color {
    val fraction = when (depth) {
        0 -> 0.06f
        1 -> 0.12f
        else -> 0.20f
    }
    return lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, fraction)
}

/** Corner radius for a nested card at [depth]; tightens as cards nest deeper, reinforcing containment. */
fun nestedCardShape(depth: Int): Shape = when (depth) {
    0 -> RoundedCornerShape(16.dp)
    1 -> RoundedCornerShape(12.dp)
    else -> RoundedCornerShape(8.dp)
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
