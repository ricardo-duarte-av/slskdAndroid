package com.slskdandroid.feature.chat.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the Chat feature. */
const val CHAT_ROUTE = "chat"

fun NavController.navigateToChat(navOptions: NavOptions? = null) {
    navigate(CHAT_ROUTE, navOptions)
}
