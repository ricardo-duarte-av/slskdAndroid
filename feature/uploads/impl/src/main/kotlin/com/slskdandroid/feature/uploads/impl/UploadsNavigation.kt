package com.slskdandroid.feature.uploads.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.feature.uploads.api.UPLOADS_ROUTE

/** Wires the Uploads route into an app-level [NavGraphBuilder]. */
fun NavGraphBuilder.uploadsScreen(
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
) {
    composable(route = UPLOADS_ROUTE) {
        UploadsRoute(onBrowseUser = onBrowseUser, onUserInfo = onUserInfo)
    }
}
