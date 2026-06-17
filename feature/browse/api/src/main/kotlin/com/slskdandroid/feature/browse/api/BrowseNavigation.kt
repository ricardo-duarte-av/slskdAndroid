package com.slskdandroid.feature.browse.api

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Browse feature. */
const val BROWSE_ROUTE = "browse"

/** Argument name and route template for opening Browse pre-targeted at a peer. */
const val BROWSE_USER_ARG = "username"
const val BROWSE_USER_ROUTE = "browse/{$BROWSE_USER_ARG}"

/** Builds the deep-link route to browse a specific peer (used from Search/Downloads/Uploads). */
fun browseUserRoute(username: String): String = "browse/${Uri.encode(username)}"

fun NavController.navigateToBrowse(navOptions: NavOptions? = null) {
    navigate(BROWSE_ROUTE, navOptions)
}
