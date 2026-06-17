package com.slskdandroid.feature.search.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.feature.search.api.SEARCH_ROUTE

/** Wires the Search route into an app-level [NavGraphBuilder]. */
fun NavGraphBuilder.searchScreen() {
    composable(route = SEARCH_ROUTE) {
        SearchRoute()
    }
}
