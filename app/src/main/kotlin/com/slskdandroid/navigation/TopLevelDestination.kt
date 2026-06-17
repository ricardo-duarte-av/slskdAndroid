package com.slskdandroid.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.ui.graphics.vector.ImageVector
import com.slskdandroid.feature.browse.api.BROWSE_ROUTE
import com.slskdandroid.feature.chat.api.CHAT_ROUTE
import com.slskdandroid.feature.downloads.api.DOWNLOADS_ROUTE
import com.slskdandroid.feature.rooms.api.ROOMS_ROUTE
import com.slskdandroid.feature.search.api.SEARCH_ROUTE
import com.slskdandroid.feature.uploads.api.UPLOADS_ROUTE
import com.slskdandroid.feature.users.api.USERS_ROUTE

/** The top-level sections shown in the adaptive navigation suite, in display order. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    SEARCH(SEARCH_ROUTE, "Search", Icons.Filled.Search),
    DOWNLOADS(DOWNLOADS_ROUTE, "Downloads", Icons.Filled.Download),
    UPLOADS(UPLOADS_ROUTE, "Uploads", Icons.Filled.Upload),
    ROOMS(ROOMS_ROUTE, "Rooms", Icons.Filled.Forum),
    CHAT(CHAT_ROUTE, "Chat", Icons.AutoMirrored.Filled.Chat),
    USERS(USERS_ROUTE, "Users", Icons.Filled.People),
    BROWSE(BROWSE_ROUTE, "Browse", Icons.Filled.Folder),
}
