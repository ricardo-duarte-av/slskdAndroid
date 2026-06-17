package com.slskdandroid.feature.search.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Search feature. */
const val SEARCH_ROUTE = "search"

fun NavController.navigateToSearch(navOptions: NavOptions? = null) {
    navigate(SEARCH_ROUTE, navOptions)
}
