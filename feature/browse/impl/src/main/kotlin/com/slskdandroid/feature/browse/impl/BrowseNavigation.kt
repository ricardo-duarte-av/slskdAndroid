package com.slskdandroid.feature.browse.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.core.designsystem.component.PlaceholderScreen
import com.slskdandroid.feature.browse.api.BROWSE_ROUTE

/** Wires the Browse route into an app-level [NavGraphBuilder]. Placeholder until built out. */
fun NavGraphBuilder.browseScreen() {
    composable(route = BROWSE_ROUTE) {
        PlaceholderScreen(title = "Browse")
    }
}
