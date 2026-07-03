package com.slskdandroid.feature.chat.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.slskdandroid.feature.chat.api.CHAT_ROUTE
import com.slskdandroid.feature.chat.api.CHAT_USER_ARG
import com.slskdandroid.feature.chat.api.CHAT_USER_ROUTE

/**
 * Wires the Chat routes into an app-level [NavGraphBuilder]: the conversation-list landing and the
 * peer deep-link (`chat/{username}`) opened from the Users/Search/Downloads/Uploads "Chat" actions,
 * which lands on the new-message composer pre-filled with that peer.
 */
fun NavGraphBuilder.chatScreen(onSettings: () -> Unit) {
    composable(route = CHAT_ROUTE) {
        ChatRoute(onSettings = onSettings)
    }
    composable(
        route = CHAT_USER_ROUTE,
        arguments = listOf(navArgument(CHAT_USER_ARG) { type = NavType.StringType }),
    ) {
        ChatRoute(onSettings = onSettings)
    }
}
