package com.slskdandroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.slskdandroid.core.data.SettingsRepository
import com.slskdandroid.core.designsystem.theme.SlskdTheme
import com.slskdandroid.feature.connection.api.CONNECTION_SETUP_ROUTE
import com.slskdandroid.feature.connection.impl.connectionSetupScreen
import com.slskdandroid.navigation.SlskdApp
import com.slskdandroid.notifications.NotificationServiceController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var settingsRepository: SettingsRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        observeNotificationService()
        setContent {
            SlskdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when (viewModel.uiState.collectAsStateWithLifecycle().value) {
                        MainUiState.Loading -> LoadingScreen()
                        MainUiState.NotConfigured -> ConnectionSetupGate()
                        MainUiState.Configured -> SlskdApp()
                    }
                }
            }
        }
    }

    /**
     * Runs the message-polling foreground service exactly when the app is both configured and has
     * notifications enabled. Requests POST_NOTIFICATIONS (Android 13+) the moment it's turned on so
     * the notifications can actually be shown.
     */
    private fun observeNotificationService() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.uiState, settingsRepository.notificationSettings) { main, settings ->
                    main == MainUiState.Configured && settings.enabled
                }.distinctUntilChanged().collect { shouldRun ->
                    if (shouldRun) {
                        ensureNotificationPermission()
                        NotificationServiceController.start(this@MainActivity)
                    } else {
                        NotificationServiceController.stop(this@MainActivity)
                    }
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Mandatory connection-setup flow shown when no connection is configured. Once verified,
 * [MainViewModel]'s state flips to Configured and the app shell ([SlskdApp]) takes over.
 */
@Composable
private fun ConnectionSetupGate() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = CONNECTION_SETUP_ROUTE,
    ) {
        connectionSetupScreen(onConnectionEstablished = { /* MainViewModel drives the switch */ })
    }
}
