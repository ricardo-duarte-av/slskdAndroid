package com.slskdandroid.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale (8dp grid, with a 4dp half-step for dense rows).
 *
 * Material's I/O 2026 guidance is an 8dp spacing system so layouts adapt predictably across device
 * sizes. The app currently mixes 2/6/10/14dp literals; those are migrated opportunistically rather
 * than in one sweep, because a blanket rewrite would shift the density of every dense list row at
 * once with no way to judge the result short of looking at each screen.
 */
object Spacing {
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 24.dp
    val ExtraLarge: Dp = 32.dp
}

/**
 * The widest a single column of text/list content should get.
 *
 * Material caps content measure on Large (1200dp+) and Extra-large (1600dp+) windows — a list
 * stretched edge to edge on a tablet or desktop window is unreadable, and every row's actions end
 * up a hand-span away from its label. 840dp is the low end of Material's 840–1040dp guidance,
 * chosen because these are dense file lists rather than prose.
 */
val ReadableMaxWidth: Dp = 840.dp

/**
 * Centres [content] and caps it at [maxWidth] on wide windows.
 *
 * **Below [maxWidth] this is a no-op** — on a phone the constraint never binds, so this changes
 * nothing there. It only takes effect once the window is wider than the cap.
 *
 * Applied to scrolling content rather than to whole screens, so app bars and bottom bars stay
 * full-bleed as the spec expects, and only the column of rows is constrained.
 */
@Composable
fun ReadableWidth(
    modifier: Modifier = Modifier,
    maxWidth: Dp = ReadableMaxWidth,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth()) { content() }
    }
}
