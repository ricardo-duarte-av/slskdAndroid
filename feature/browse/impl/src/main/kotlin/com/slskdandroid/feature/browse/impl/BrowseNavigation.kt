package com.slskdandroid.feature.browse.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.slskdandroid.feature.browse.api.BROWSE_ROUTE
import com.slskdandroid.feature.browse.api.BROWSE_USER_ARG
import com.slskdandroid.feature.browse.api.BROWSE_USER_ROUTE

/**
 * Wires the Browse routes into an app-level [NavGraphBuilder]: the typed-username landing and the
 * peer deep-link (`browse/{username}`) opened from Search/Downloads/Uploads.
 */
fun NavGraphBuilder.browseScreen() {
    composable(route = BROWSE_ROUTE) {
        BrowseRoute()
    }
    composable(
        route = BROWSE_USER_ROUTE,
        arguments = listOf(navArgument(BROWSE_USER_ARG) { type = NavType.StringType }),
    ) {
        BrowseRoute()
    }
}
