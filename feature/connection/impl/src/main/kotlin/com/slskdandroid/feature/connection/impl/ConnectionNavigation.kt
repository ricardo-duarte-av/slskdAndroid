package com.slskdandroid.feature.connection.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.feature.connection.api.CONNECTION_SETUP_ROUTE

/** Wires the connection-setup route into an app-level [NavGraphBuilder]. */
fun NavGraphBuilder.connectionSetupScreen(
    onConnectionEstablished: () -> Unit,
) {
    composable(route = CONNECTION_SETUP_ROUTE) {
        ConnectionSetupRoute(onConnectionEstablished = onConnectionEstablished)
    }
}
