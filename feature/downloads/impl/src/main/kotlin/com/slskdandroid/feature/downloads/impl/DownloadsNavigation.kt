package com.slskdandroid.feature.downloads.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.core.designsystem.component.PlaceholderScreen
import com.slskdandroid.feature.downloads.api.DOWNLOADS_ROUTE

/** Wires the Downloads route into an app-level [NavGraphBuilder]. Placeholder until built out. */
fun NavGraphBuilder.downloadsScreen() {
    composable(route = DOWNLOADS_ROUTE) {
        PlaceholderScreen(title = "Downloads")
    }
}
