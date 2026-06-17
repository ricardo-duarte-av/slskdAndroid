package com.slskdandroid.feature.downloads.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Downloads feature. */
const val DOWNLOADS_ROUTE = "downloads"

fun NavController.navigateToDownloads(navOptions: NavOptions? = null) {
    navigate(DOWNLOADS_ROUTE, navOptions)
}
