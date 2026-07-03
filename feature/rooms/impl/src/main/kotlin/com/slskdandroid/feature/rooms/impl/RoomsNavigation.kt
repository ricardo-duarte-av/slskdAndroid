package com.slskdandroid.feature.rooms.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.feature.rooms.api.ROOMS_ROUTE

/**
 * Wires the Rooms route into an app-level [NavGraphBuilder]. [onUserInfo] opens a room member's
 * profile in the Users tab.
 */
fun NavGraphBuilder.roomsScreen(onUserInfo: (String) -> Unit, onSettings: () -> Unit) {
    composable(route = ROOMS_ROUTE) {
        RoomsRoute(onUserInfo = onUserInfo, onSettings = onSettings)
    }
}
