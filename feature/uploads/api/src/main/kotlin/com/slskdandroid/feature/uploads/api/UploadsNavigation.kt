package com.slskdandroid.feature.uploads.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Uploads feature. */
const val UPLOADS_ROUTE = "uploads"

fun NavController.navigateToUploads(navOptions: NavOptions? = null) {
    navigate(UPLOADS_ROUTE, navOptions)
}
