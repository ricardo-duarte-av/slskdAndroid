package com.slskdandroid.feature.rooms.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.core.designsystem.component.PlaceholderScreen
import com.slskdandroid.feature.rooms.api.ROOMS_ROUTE

/** Wires the Rooms route into an app-level [NavGraphBuilder]. Placeholder until built out. */
fun NavGraphBuilder.roomsScreen() {
    composable(route = ROOMS_ROUTE) {
        PlaceholderScreen(title = "Rooms")
    }
}
