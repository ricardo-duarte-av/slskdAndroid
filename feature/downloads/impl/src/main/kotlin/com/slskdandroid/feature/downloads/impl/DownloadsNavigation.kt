package com.slskdandroid.feature.downloads.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.feature.downloads.api.DOWNLOADS_ROUTE

/** Wires the Downloads route into an app-level [NavGraphBuilder]. */
fun NavGraphBuilder.downloadsScreen(
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
) {
    composable(route = DOWNLOADS_ROUTE) {
        DownloadsRoute(onBrowseUser = onBrowseUser, onUserInfo = onUserInfo)
    }
}
