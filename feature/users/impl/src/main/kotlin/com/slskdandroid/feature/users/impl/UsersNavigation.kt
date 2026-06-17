package com.slskdandroid.feature.users.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.core.designsystem.component.PlaceholderScreen
import com.slskdandroid.feature.users.api.USERS_ROUTE

/** Wires the Users route into an app-level [NavGraphBuilder]. Placeholder until built out. */
fun NavGraphBuilder.usersScreen() {
    composable(route = USERS_ROUTE) {
        PlaceholderScreen(title = "Users")
    }
}
