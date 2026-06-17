package com.slskdandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.slskdandroid.core.designsystem.theme.SlskdTheme
import com.slskdandroid.feature.connection.api.CONNECTION_SETUP_ROUTE
import com.slskdandroid.feature.connection.impl.connectionSetupScreen
import com.slskdandroid.feature.search.api.SEARCH_ROUTE
import com.slskdandroid.feature.search.api.navigateToSearch
import com.slskdandroid.feature.search.impl.searchScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SlskdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when (val state = viewModel.uiState.collectAsStateWithLifecycle().value) {
                        MainUiState.Loading -> LoadingScreen()
                        MainUiState.NotConfigured -> SlskdNavHost(startConfigured = false)
                        MainUiState.Configured -> SlskdNavHost(startConfigured = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SlskdNavHost(
    startConfigured: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = if (startConfigured) SEARCH_ROUTE else CONNECTION_SETUP_ROUTE,
    ) {
        connectionSetupScreen(
            onConnectionEstablished = {
                navController.navigateToSearch(
                    navOptions = androidx.navigation.navOptions {
                        popUpTo(CONNECTION_SETUP_ROUTE) { inclusive = true }
                    },
                )
            },
        )
        searchScreen()
    }
}
