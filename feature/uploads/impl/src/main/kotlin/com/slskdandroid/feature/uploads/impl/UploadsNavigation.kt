package com.slskdandroid.feature.uploads.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.core.designsystem.component.PlaceholderScreen
import com.slskdandroid.feature.uploads.api.UPLOADS_ROUTE

/** Wires the Uploads route into an app-level [NavGraphBuilder]. Placeholder until built out. */
fun NavGraphBuilder.uploadsScreen() {
    composable(route = UPLOADS_ROUTE) {
        PlaceholderScreen(title = "Uploads")
    }
}
