package com.slskdandroid.feature.chat.api

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Chat (direct-message) feature. */
const val CHAT_ROUTE = "chat"

/** Argument name and route template for opening Chat pre-targeted at a peer (compose a message). */
const val CHAT_USER_ARG = "username"
const val CHAT_USER_ROUTE = "chat/{$CHAT_USER_ARG}"

/**
 * Builds the deep-link route to compose a message to [username] (used from the Users/Search/
 * Downloads/Uploads "Chat" actions): opens the Chat tab with the new-message composer pre-filled.
 */
fun chatUserRoute(username: String): String = "chat/${Uri.encode(username)}"

fun NavController.navigateToChat(navOptions: NavOptions? = null) {
    navigate(CHAT_ROUTE, navOptions)
}
