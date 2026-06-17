package com.slskdandroid.feature.browse.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Browse feature. */
const val BROWSE_ROUTE = "browse"

fun NavController.navigateToBrowse(navOptions: NavOptions? = null) {
    navigate(BROWSE_ROUTE, navOptions)
}
