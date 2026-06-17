package com.slskdandroid.feature.search.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Search feature. */
const val SEARCH_ROUTE = "search"

/** Argument name and route template for the per-search detail screen. */
const val SEARCH_ID_ARG = "searchId"
const val SEARCH_DETAIL_ROUTE = "search/{$SEARCH_ID_ARG}"

/** Builds the detail route for a concrete search id. */
fun searchDetailRoute(searchId: String): String = "search/$searchId"

fun NavController.navigateToSearch(navOptions: NavOptions? = null) {
    navigate(SEARCH_ROUTE, navOptions)
}
