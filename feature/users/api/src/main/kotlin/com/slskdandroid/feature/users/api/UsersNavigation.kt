package com.slskdandroid.feature.users.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Users feature. */
const val USERS_ROUTE = "users"

fun NavController.navigateToUsers(navOptions: NavOptions? = null) {
    navigate(USERS_ROUTE, navOptions)
}
