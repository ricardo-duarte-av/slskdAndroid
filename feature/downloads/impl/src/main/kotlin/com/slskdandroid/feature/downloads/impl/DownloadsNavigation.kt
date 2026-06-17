package com.slskdandroid.feature.downloads.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.feature.downloads.api.DOWNLOADS_ROUTE

/** Wires the Downloads route into an app-level [NavGraphBuilder]. */
fun NavGraphBuilder.downloadsScreen() {
    composable(route = DOWNLOADS_ROUTE) {
        DownloadsRoute()
    }
}
