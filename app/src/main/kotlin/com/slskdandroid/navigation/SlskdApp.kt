package com.slskdandroid.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.slskdandroid.feature.browse.impl.browseScreen
import com.slskdandroid.feature.chat.impl.chatScreen
import com.slskdandroid.feature.downloads.impl.downloadsScreen
import com.slskdandroid.feature.rooms.impl.roomsScreen
import com.slskdandroid.feature.search.impl.searchScreen
import com.slskdandroid.feature.uploads.impl.uploadsScreen
import com.slskdandroid.feature.users.impl.usersScreen

/**
 * The signed-in app shell: an adaptive navigation suite (bottom bar on compact, rail on
 * medium, drawer on expanded) over a NavHost of the top-level destinations.
 */
@Composable
fun SlskdApp(
    navController: NavHostController = rememberNavController(),
) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                item(
                    selected = selected,
                    onClick = { navController.navigateToTopLevel(destination) },
                    // Icons only — no text label. contentDescription keeps it accessible.
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.SEARCH.route,
        ) {
            searchScreen()
            downloadsScreen()
            uploadsScreen()
            roomsScreen()
            chatScreen()
            usersScreen()
            browseScreen()
        }
    }
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        // Single-top, and pop back to the start destination so the back stack doesn't grow
        // as the user hops between tabs (standard bottom-nav behaviour).
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
