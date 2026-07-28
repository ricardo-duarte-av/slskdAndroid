package com.slskdandroid.navigation

import androidx.annotation.StringRes
import com.slskdandroid.R

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
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    SEARCH(SEARCH_ROUTE, R.string.nav_search, Icons.Filled.Search),
    DOWNLOADS(DOWNLOADS_ROUTE, R.string.nav_downloads, Icons.Filled.Download),
    UPLOADS(UPLOADS_ROUTE, R.string.nav_uploads, Icons.Filled.Upload),
    ROOMS(ROOMS_ROUTE, R.string.nav_rooms, Icons.Filled.Forum),
    CHAT(CHAT_ROUTE, R.string.nav_chat, Icons.AutoMirrored.Filled.Chat),
    USERS(USERS_ROUTE, R.string.nav_users, Icons.Filled.People),
    BROWSE(BROWSE_ROUTE, R.string.nav_browse, Icons.Filled.Folder),
}
