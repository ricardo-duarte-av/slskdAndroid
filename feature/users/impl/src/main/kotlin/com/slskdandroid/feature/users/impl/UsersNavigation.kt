package com.slskdandroid.feature.users.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.slskdandroid.feature.users.api.USERS_ROUTE
import com.slskdandroid.feature.users.api.USERS_USER_ARG
import com.slskdandroid.feature.users.api.USERS_USER_ROUTE

/**
 * Wires the Users routes into an app-level [NavGraphBuilder]: the typed-username landing and the
 * peer deep-link (`users/{username}`) opened from Search/Downloads/Uploads. [onBrowseUser] opens
 * the peer in the Browse tab; [onChatUser] will open a direct-message conversation once Chat is
 * built out.
 */
fun NavGraphBuilder.usersScreen(
    onBrowseUser: (String) -> Unit,
    onChatUser: (String) -> Unit,
) {
    composable(route = USERS_ROUTE) {
        UsersRoute(onBrowseUser = onBrowseUser, onChatUser = onChatUser)
    }
    composable(
        route = USERS_USER_ROUTE,
        arguments = listOf(navArgument(USERS_USER_ARG) { type = NavType.StringType }),
    ) {
        UsersRoute(onBrowseUser = onBrowseUser, onChatUser = onChatUser)
    }
}
