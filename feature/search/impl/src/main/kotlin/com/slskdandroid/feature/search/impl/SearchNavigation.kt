package com.slskdandroid.feature.search.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.slskdandroid.feature.search.api.SEARCH_DETAIL_ROUTE
import com.slskdandroid.feature.search.api.SEARCH_ROUTE

/** Wires the Search list route into an app-level [NavGraphBuilder]. */
fun NavGraphBuilder.searchListScreen(onOpenSearch: (String) -> Unit, onSettings: () -> Unit) {
    composable(route = SEARCH_ROUTE) {
        SearchListRoute(onOpenSearch = onOpenSearch, onSettings = onSettings)
    }
}

/** Wires the per-search detail route into an app-level [NavGraphBuilder]. */
fun NavGraphBuilder.searchDetailScreen(
    onBack: () -> Unit,
    onBrowseUser: (String) -> Unit,
    onUserInfo: (String) -> Unit,
    onChatUser: (String) -> Unit,
) {
    composable(
        route = SEARCH_DETAIL_ROUTE,
        arguments = listOf(navArgument("searchId") { type = NavType.StringType }),
    ) {
        SearchDetailRoute(
            onBack = onBack,
            onBrowseUser = onBrowseUser,
            onUserInfo = onUserInfo,
            onChatUser = onChatUser,
        )
    }
}
