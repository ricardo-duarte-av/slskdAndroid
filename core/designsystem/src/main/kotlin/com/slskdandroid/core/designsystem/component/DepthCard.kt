package com.slskdandroid.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse

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

/**
 * Content color paired with [nestedCardColor] at the same [depth].
 *
 * Only consulted when the container isn't a scheme role that `contentColorFor` already knows (see
 * [DepthCard]) — i.e. for the accent tint, whose blended container has no built-in `on*` partner.
 */
@Composable
fun nestedCardContentColor(depth: Int): Color =
    if (LocalUseAccentCards.current) accentCardContentColor(depth) else MaterialTheme.colorScheme.onSurface

/** How strongly the accent tint is mixed into the surface at each depth. */
private fun accentFraction(depth: Int): Float = when (depth) {
    0 -> 0.06f
    1 -> 0.12f
    else -> 0.20f
}

/**
 * Accent container: `primaryContainer` blended over the surface, deepening with depth.
 *
 * Blends toward **primaryContainer**, not `primary`. Both endpoints of this interpolation are a
 * valid Material pair (`surface`/`onSurface` and `primaryContainer`/`onPrimaryContainer`), so
 * blending container and content in lockstep (see [accentCardContentColor]) keeps a legible
 * pairing at every fraction. Blending toward `primary` had no `on*` partner to travel with, which
 * left `onSurface` text sitting on an increasingly primary-tinted background at depth 2.
 */
@Composable
private fun accentCardColor(depth: Int): Color = lerp(
    MaterialTheme.colorScheme.surface,
    MaterialTheme.colorScheme.primaryContainer,
    accentFraction(depth),
)

/** The `on*` partner of [accentCardColor], blended by the same fraction so the pairing holds. */
@Composable
private fun accentCardContentColor(depth: Int): Color = lerp(
    MaterialTheme.colorScheme.onSurface,
    MaterialTheme.colorScheme.onPrimaryContainer,
    accentFraction(depth),
)

/**
 * Corner radius for a nested card at [depth]; tightens as cards nest deeper, reinforcing
 * containment.
 *
 * Reads the theme's shape scale rather than hardcoding dp, so retheming moves the cards with it.
 * The roles chosen (large/medium/small) resolve to the same 16/12/8dp the literals used, so this
 * is behaviour-preserving today — but it is now a token, not a magic number.
 */
@Composable
fun nestedCardShape(depth: Int): Shape = when (depth) {
    0 -> MaterialTheme.shapes.large
    1 -> MaterialTheme.shapes.medium
    else -> MaterialTheme.shapes.small
}

/**
 * A nested result card at [depth] (see [nestedCardColor]). Pass [color] to override the tonal
 * default — e.g. a selection highlight on a file row.
 *
 * [contentColor] resolves in the Material way first: a [color] that *is* a scheme role (the neutral
 * ladder, or a caller's `secondaryContainer` selection highlight) yields its declared `on*` partner
 * via `contentColorFor`. Only a blended container — which `contentColorFor` can't match and would
 * otherwise answer `Unspecified` for — falls back to [nestedCardContentColor].
 */
@Composable
fun DepthCard(
    depth: Int,
    modifier: Modifier = Modifier,
    color: Color = nestedCardColor(depth),
    contentColor: Color = contentColorFor(color).takeOrElse { nestedCardContentColor(depth) },
    content: @Composable () -> Unit,
) {
    Surface(
        color = color,
        contentColor = contentColor,
        shape = nestedCardShape(depth),
        modifier = modifier,
        content = content,
    )
}
