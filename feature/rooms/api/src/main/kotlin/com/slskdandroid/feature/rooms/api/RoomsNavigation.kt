package com.slskdandroid.feature.rooms.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Rooms feature. */
const val ROOMS_ROUTE = "rooms"

fun NavController.navigateToRooms(navOptions: NavOptions? = null) {
    navigate(ROOMS_ROUTE, navOptions)
}
