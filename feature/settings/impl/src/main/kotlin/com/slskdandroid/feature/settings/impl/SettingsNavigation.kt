package com.slskdandroid.feature.settings.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slskdandroid.feature.settings.api.SETTINGS_ROUTE

/** Wires the Settings route into an app-level [NavGraphBuilder]. [onBack] pops back to the caller. */
fun NavGraphBuilder.settingsScreen(onBack: () -> Unit) {
    composable(route = SETTINGS_ROUTE) {
        SettingsRoute(onBack = onBack)
    }
}
