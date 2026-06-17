package com.slskdandroid.feature.connection.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

/** Public navigation contract for the mandatory connection-setup feature. */
const val CONNECTION_SETUP_ROUTE = "connection_setup"

fun NavController.navigateToConnectionSetup(navOptions: NavOptions? = null) {
    navigate(CONNECTION_SETUP_ROUTE, navOptions)
}
