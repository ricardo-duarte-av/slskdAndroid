package com.slskdandroid.feature.users.api

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Users feature. */
const val USERS_ROUTE = "users"

/** Argument name and route template for opening Users pre-targeted at a peer. */
const val USERS_USER_ARG = "username"
const val USERS_USER_ROUTE = "users/{$USERS_USER_ARG}"

/** Builds the deep-link route to a peer's profile (used from Search/Downloads/Uploads). */
fun usersUserRoute(username: String): String = "users/${Uri.encode(username)}"

fun NavController.navigateToUsers(navOptions: NavOptions? = null) {
    navigate(USERS_ROUTE, navOptions)
}
