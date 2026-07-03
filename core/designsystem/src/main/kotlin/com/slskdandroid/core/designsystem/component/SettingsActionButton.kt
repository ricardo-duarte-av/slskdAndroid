package com.slskdandroid.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * The shared "open Settings" action for top-level screens' app bars. Drop into a `TopAppBar`'s
 * `actions` slot so every section exposes Settings from its top-right corner consistently.
 */
@Composable
fun SettingsActionButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Settings, contentDescription = "Settings")
    }
}
