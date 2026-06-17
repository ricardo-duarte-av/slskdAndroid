package com.slskdandroid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.ConnectionSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Resolves whether a slskd connection is configured, gating the app's start destination. */
sealed interface MainUiState {
    data object Loading : MainUiState
    data object NotConfigured : MainUiState
    data object Configured : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    connectionSettingsRepository: ConnectionSettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = connectionSettingsRepository.connectionSettings
        .map { settings ->
            if (settings == null) MainUiState.NotConfigured else MainUiState.Configured
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState.Loading,
        )
}
