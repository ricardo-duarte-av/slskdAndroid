package com.slskdandroid.feature.chat.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.core.designsystem.component.PlaceholderScreen
import com.slskdandroid.feature.chat.api.CHAT_ROUTE

/** Wires the Chat route into an app-level [NavGraphBuilder]. Placeholder until built out. */
fun NavGraphBuilder.chatScreen() {
    composable(route = CHAT_ROUTE) {
        PlaceholderScreen(title = "Chat")
    }
}
